package com.nhd.mongoseeder.config;

import com.nhd.mongoseeder.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class MongoHealthMonitor {

    private final MongoTemplate mongoTemplate;
    private final JobService jobService;
    private final AtomicBoolean mongoUp = new AtomicBoolean(true);

    public MongoHealthMonitor(MongoTemplate mongoTemplate, JobService jobService) {
        this.mongoTemplate = mongoTemplate;
        this.jobService = jobService;
    }

    @Scheduled(fixedDelay = 3000)
    public void checkMongoConnection() {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");

            if (!mongoUp.get()) {
                mongoUp.set(true);
                log.info("MongoDB connection restored.");
            }

        } catch (Exception e) {
            if (mongoUp.getAndSet(false)) {
                log.error("Lost connection to MongoDB: {}", e.getMessage());
                jobService.markAllJobsFailed("MongoDB connection lost");
            }
        }
    }
}

