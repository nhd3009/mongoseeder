package com.nhd.mongoseeder.model;

import com.nhd.mongoseeder.dto.JobConfig;
import com.nhd.mongoseeder.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DataJob {
    private String id;
    private JobConfig config;
    private volatile JobStatus status;
    private JobMetrics metrics;
    private volatile boolean stopRequested = false;


    private final List<String> lastErrors = new CopyOnWriteArrayList<>();

    public DataJob(String id, JobConfig config) {
        this.id = id;
        this.config = config;
        this.status = JobStatus.PENDING;
        this.metrics = new JobMetrics();
    }

    public void addError(String error) {
        if (lastErrors.size() >= 10) {
            lastErrors.remove(0);
        }
        lastErrors.add(error);
        metrics.getErrorCount().incrementAndGet();
    }

    public void requestStop() {
        this.stopRequested = true;
    }
}
