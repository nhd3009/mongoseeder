package com.nhd.mongoseeder.model;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class JobMetrics {
    private long startTime;
    private Long endTime;
    private AtomicInteger insertedRecords = new AtomicInteger(0);
    private AtomicInteger failedRecords = new AtomicInteger(0);
    private AtomicInteger errorCount = new AtomicInteger(0);

    public double getRecordsPerSecond() {
        if (startTime == 0) return 0;
        long currentTime = (endTime != null) ? endTime : System.currentTimeMillis();
        long durationSec = (currentTime - startTime) / 1000;
        if (durationSec == 0) return insertedRecords.get();
        return (double) insertedRecords.get() / durationSec;
    }
}
