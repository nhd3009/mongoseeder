package com.nhd.mongoseeder.service;

import com.mongodb.client.MongoCollection;
import com.nhd.mongoseeder.config.JsonSchemaValidator;
import com.nhd.mongoseeder.config.MongoTemplateFactory;
import com.nhd.mongoseeder.dto.JobConfig;
import com.nhd.mongoseeder.engine.FakeDataEngine;
import com.nhd.mongoseeder.enums.JobStatus;
import com.nhd.mongoseeder.model.DataJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.graalvm.polyglot.Value;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final Map<String, DataJob> jobStore = new ConcurrentHashMap<>();
    private final MongoTemplateFactory templateFactory;

    private final ThreadLocal<FakeDataEngine> engineThreadLocal =
            ThreadLocal.withInitial(FakeDataEngine::new);

    public DataJob createJob(JobConfig config) {
        log.info(
                "Creating job with config: collection={}, totalRecords={}, batchSize={}, threads={}",
                config.getCollectionName(), config.getTotalRecords(), config.getBatchSize(),
                config.getThreadCount());
        JsonSchemaValidator.validateSchema(config.getSchemaJson());
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
        DataJob job = jobStore.get(jobId);
        
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            log.warn("Cannot start job [{}]: not found or not in PENDING state.", jobId);
            return;
        }

        MongoTemplate mongoTemplate = templateFactory.create(job.getConfig().getDatabaseName());
        MongoCollection<Document> collection = mongoTemplate.getCollection(job.getConfig().getCollectionName());

        log.info("Starting job [{}]...", jobId);
        job.setStatus(JobStatus.RUNNING);
        job.getMetrics().setStartTime(System.currentTimeMillis());

        ExecutorService jobExecutor =
                Executors.newFixedThreadPool(job.getConfig().getThreadCount());
        boolean failed = false;

        try {
            int total = job.getConfig().getTotalRecords();
            int batchSize = job.getConfig().getBatchSize();
            int totalBatches = (int) Math.ceil((double) total / batchSize);

            AtomicInteger batchCounter = new AtomicInteger(0);

            for (int t = 0; t < job.getConfig().getThreadCount(); t++) {

                jobExecutor.submit(() -> {

                    while (true) {
                        if (job.isStopRequested())
                            break;
                        int batchIndex = batchCounter.getAndIncrement();

                        if (batchIndex >= totalBatches || job.isStopRequested())
                            break;

                        int currentBatchSize = (batchIndex == totalBatches - 1)
                                        ? total - (batchIndex * batchSize)
                                        : batchSize;

                        try {
                            if (job.isStopRequested())
                                break;
                            processSingleBatch(job, currentBatchSize, collection);
                            log.debug("Job [{}]: completed batch {}", jobId, batchIndex);

                        } catch (Exception e) {

                            job.addError(e.getMessage());
                            log.error("Job [{}]: error in batch {}: {}", jobId, batchIndex,
                                    e.getMessage(), e);
                            if (isMongoConnectionError(e)) {
                                log.error("Job [{}]: MongoDB connection lost, marking FAILED", jobId);
                                job.requestStop();
                                synchronized (job) {
                                    job.setStatus(JobStatus.FAILED);
                                }
                                jobExecutor.shutdownNow();
                                break;
                            }
                        }
                    }
                });
            }
            jobExecutor.shutdown();
            jobExecutor.awaitTermination(1, TimeUnit.HOURS);   
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.addError("Job interrupted: " + e.getMessage());
            job.setStatus(JobStatus.FAILED);
            log.error("Job [{}] interrupted: {}", jobId, e.getMessage());
            failed = true;
        } finally {
            jobExecutor.shutdown();
            job.getMetrics().setEndTime(System.currentTimeMillis());
            long duration = job.getMetrics().getEndTime() - job.getMetrics().getStartTime();

            if (job.getStatus() == JobStatus.FAILED || failed) {
                log.error("Job [{}] FAILED after {} ms ({} records inserted)", jobId, duration,
                        job.getMetrics().getInsertedRecords().get());
            } else if (job.isStopRequested()) {
                job.setStatus(JobStatus.STOPPED);
                log.warn("Job [{}] STOPPED by user after {} ms", jobId, duration);
            } else {
                job.setStatus(JobStatus.COMPLETED);
                log.info("Job [{}] COMPLETED successfully after {} ms ({} records inserted)", jobId,
                        duration, job.getMetrics().getInsertedRecords().get());
            }

        }
    }

    private void processSingleBatch(DataJob job,
                                int batchSize,
                                MongoCollection<Document> collection) throws Exception {

        FakeDataEngine engine = engineThreadLocal.get();

        Value jsArray =
                engine.generateBatch(job.getConfig().getSchemaJson(), batchSize);

        List<Document> docs = new ArrayList<>(batchSize);

        for (int i = 0; i < jsArray.getArraySize(); i++) {

            Value obj = jsArray.getArrayElement(i);

            Document doc = new Document();

            for (String key : obj.getMemberKeys()) {
                doc.put(key, obj.getMember(key).as(Object.class));
            }

            docs.add(doc);
        }

        collection.insertMany(docs);

        job.getMetrics().getInsertedRecords().addAndGet(batchSize);
    }

    private boolean isMongoConnectionError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
            if (cause instanceof com.mongodb.MongoException || msg.contains("connection")
                    || msg.contains("timeout")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    public void markAllJobsFailed(String reason) {
        jobStore.values().forEach(job -> {
            if (job.getStatus() == JobStatus.RUNNING) {
                job.requestStop();
                job.setStatus(JobStatus.FAILED);
                job.addError(reason);
                log.error("Job [{}] marked FAILED due to: {}", job.getId(), reason);
            }
        });
    }
}
