package com.nhd.mongoseeder.controller;

import com.nhd.mongoseeder.dto.JobConfig;
import com.nhd.mongoseeder.enums.JobStatus;
import com.nhd.mongoseeder.model.DataJob;
import com.nhd.mongoseeder.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<DataJob> createJob(@Valid @ModelAttribute JobConfig config) {
        return ResponseEntity.ok(jobService.createJob(config));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<String> startJob(@PathVariable String id) {
        jobService.startJobExecution(id);
        return ResponseEntity.ok("Job " + id + " started.");
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<String> stopJob(@PathVariable String id) {
        jobService.stopJob(id);
        return ResponseEntity.ok("Stop signal sent for job " + id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataJob> getJob(@PathVariable String id) {
        DataJob job = jobService.getJob(id);
        return job != null ? ResponseEntity.ok(job) : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(id, emitter);

        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError((e) -> emitters.remove(id));

        return emitter;
    }

    @Scheduled(fixedRate = 1000)
    public void pushMetrics() {
        emitters.forEach((jobId, emitter) -> {
            DataJob job = jobService.getJob(jobId);
            if (job != null) {
                try {
                    emitter.send(job);
                    if (job.getStatus() == JobStatus.COMPLETED ||
                            job.getStatus() == JobStatus.STOPPED ||
                            job.getStatus() == JobStatus.FAILED) {
                        emitter.complete();
                    }
                } catch (Exception e) {
                    emitters.remove(jobId);
                }
            }
        });
    }
}
