package com.nhd.mongoseeder.service;

import com.mongodb.client.MongoClient;
import com.nhd.mongoseeder.dto.JobConfig;
import com.nhd.mongoseeder.engine.FakeDataEngine;
import com.nhd.mongoseeder.enums.JobStatus;
import com.nhd.mongoseeder.model.DataJob;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.slf4j.MDC;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final Map<String, DataJob> jobStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final MongoClient mongoClient;

    private final ThreadLocal<FakeDataEngine> engineThreadLocal =
            ThreadLocal.withInitial(FakeDataEngine::new);

    public DataJob createJob(JobConfig config) {
        log.info(
                "Creating job with config: collection={}, totalRecords={}, batchSize={}, threads={}",
                config.getCollectionName(),
                config.getTotalRecords(), 
                config.getBatchSize(),
                config.getThreadCount()
        );
        try {
            objectMapper.readTree(config.getSchemaJson());
        } catch (Exception e) {
            log.error("Invalid JSON Schema for job: {}", e.getMessage());
            throw new IllegalArgumentException(
                    "JSON Schema is not valid: " + e.getMessage()
            );
        }

        String id = UUID.randomUUID().toString();
        DataJob job = new DataJob(id, config);
        jobStore.put(id, job);
        log.info("Job [{}] created successfully.", id);
        return job;
    }

    public DataJob getJob(String id) {
        return jobStore.get(id);
    }

    public void stopJob(String id) {
        DataJob job = jobStore.get(id);
        if (job != null && job.getStatus() == JobStatus.RUNNING) {
            log.warn("Stop requested for job [{}]", id);
            job.requestStop();
        } else {
            log.info("Job [{}] is not running or not found, cannot stop.", id);
        }
    }

    @Async
    public void startJobExecution(String jobId) {
        MDC.put("jobId", jobId);
        DataJob job = jobStore.get(jobId);
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            log.warn(
                    "Cannot start job [{}]: not found or not in PENDING state.",
                    jobId
            );
            return;
        }

        log.info("Starting job [{}]...", jobId);
        job.setStatus(JobStatus.RUNNING);
        job.getMetrics().setStartTime(System.currentTimeMillis());

        ExecutorService jobExecutor = Executors.newFixedThreadPool(
                job.getConfig().getThreadCount()
        );
        boolean failed = false;

        try {
            int total = job.getConfig().getTotalRecords();
            int batchSize = job.getConfig().getBatchSize();
            int totalBatches = (int) Math.ceil((double) total / batchSize);

            CountDownLatch latch = new CountDownLatch(totalBatches);

            for (int i = 0; i < totalBatches; i++) {
                if (job.isStopRequested()) {
                    log.warn(
                            "Job [{}] stop requested before batch {}",
                            jobId,
                            i
                    );
                    while (latch.getCount() > 0) latch.countDown();
                    break;
                }

                final int batchIndex = i;
                final int currentBatchSize = (i == totalBatches - 1)
                        ? total - (i * batchSize)
                        : batchSize;

                jobExecutor.submit(() -> {
                    try {
                        if (job.isStopRequested()) return;

                        processSingleBatch(job, currentBatchSize);
                        log.debug(
                                "Job [{}]: completed batch {}",
                                jobId,
                                batchIndex
                        );
                    } catch (Exception e) {
                        job.addError(e.getMessage());
                        log.error(
                                "Job [{}]: error in batch {}: {}",
                                jobId,
                                batchIndex,
                                e.getMessage(),
                                e
                        );

                        if (isMongoConnectionError(e)) {
                            log.error(
                                    "Job [{}]: MongoDB connection lost, marking FAILED",
                                    jobId
                            );
                            job.requestStop();
                            synchronized (job) {
                                job.setStatus(JobStatus.FAILED);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.addError("Job interrupted: " + e.getMessage());
            job.setStatus(JobStatus.FAILED);
            log.error("Job [{}] interrupted: {}", jobId, e.getMessage());
            failed = true;
        } finally {
            jobExecutor.shutdown();
            job.getMetrics().setEndTime(System.currentTimeMillis());
            long duration =
                    job.getMetrics().getEndTime() - job.getMetrics().getStartTime();

            if (job.getStatus() == JobStatus.FAILED || failed) {
                log.error(
                        "Job [{}] FAILED after {} ms ({} records inserted)",
                        jobId,
                        duration,
                        job.getMetrics().getInsertedRecords().get()
                );
            } else if (job.isStopRequested()) {
                job.setStatus(JobStatus.STOPPED);
                log.warn(
                        "Job [{}] STOPPED by user after {} ms",
                        jobId,
                        duration
                );
            } else {
                job.setStatus(JobStatus.COMPLETED);
                log.info(
                        "Job [{}] COMPLETED successfully after {} ms ({} records inserted)",
                        jobId,
                        duration,
                        job.getMetrics().getInsertedRecords().get()
                );
            }

            MDC.remove("jobId");
        }
    }

    private void processSingleBatch(DataJob job, int batchSize)
            throws Exception {
        FakeDataEngine engine = engineThreadLocal.get();

        String jsonArray = engine.generateBatchJson(
                job.getConfig().getSchemaJson(),
                batchSize
        );

        List<Map<String, Object>> list = objectMapper.readValue(
                jsonArray,
                new TypeReference<>() {}
        );

        List<Document> docs = list.stream()
                .map(Document::new)
                .toList();

        MongoTemplate mongoTemplate = new MongoTemplate(
                mongoClient,
                job.getConfig().getDatabaseName()
        );

        mongoTemplate.insert(docs, job.getConfig().getCollectionName());

        job.getMetrics().getInsertedRecords().addAndGet(batchSize);
    }

    private boolean isMongoConnectionError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String msg =
                    cause.getMessage() != null
                            ? cause.getMessage().toLowerCase()
                            : "";
            if (
                    cause instanceof com.mongodb.MongoException ||
                            msg.contains("connection") ||
                            msg.contains("timeout")
            ) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    public void markAllJobsFailed(String reason) {
        jobStore
                .values()
                .forEach(job -> {
                    if (job.getStatus() == JobStatus.RUNNING) {
                        job.requestStop();
                        job.setStatus(JobStatus.FAILED);
                        job.addError(reason);
                        log.error(
                                "Job [{}] marked FAILED due to: {}",
                                job.getId(),
                                reason
                        );
                    }
                });
    }
}
