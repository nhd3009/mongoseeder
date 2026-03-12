package com.nhd.mongoseeder.service;

import com.nhd.mongoseeder.config.JsonSchemaValidator;
import com.nhd.mongoseeder.config.MongoTemplateFactory;
import com.nhd.mongoseeder.dto.JobConfig;
import com.nhd.mongoseeder.enums.JobStatus;
import com.nhd.mongoseeder.model.DataJob;
import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private JobService jobService;
    private ObjectMapper objectMapper;

    @Mock
    private MongoTemplateFactory templateFactory;

    @Mock
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jobService = new JobService(objectMapper, templateFactory);
    }

    private JobConfig createValidConfig() {
        JobConfig config = new JobConfig();
        config.setDatabaseName("testDb");
        config.setCollectionName("testColl");
        config.setTotalRecords(5);
        config.setBatchSize(5);
        config.setThreadCount(1);
        config.setSchemaJson("{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}}");
        return config;
    }

    @Test
    void testStartAndStopJob() {
        JobConfig config = createValidConfig();
        DataJob job = jobService.createJob(config);

        assertEquals(JobStatus.PENDING, job.getStatus());

        job.setStatus(JobStatus.RUNNING);
        jobService.stopJob(job.getId());

        assertTrue(job.isStopRequested());
    }

    @Test
    void testInsertBatchSuccessAndMetricsUpdate() {
        JobConfig config = createValidConfig();
        DataJob job = jobService.createJob(config);

        when(templateFactory.create("testDb")).thenReturn(mongoTemplate);

        jobService.startJobExecution(job.getId());

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(5, job.getMetrics().getInsertedRecords().get());
        assertTrue(job.getMetrics().getStartTime() > 0);
        assertTrue(job.getMetrics().getEndTime() >= job.getMetrics().getStartTime());

        verify(mongoTemplate, times(1)).insert(anyCollection(), eq("testColl"));
    }

    @Test
    void testMongoErrorMarksJobFailed() {
        JobConfig config = createValidConfig();
        DataJob job = jobService.createJob(config);

        when(templateFactory.create("testDb")).thenReturn(mongoTemplate);

        doThrow(new MongoException("Connection lost"))
            .when(mongoTemplate)
            .insert(anyCollection(), eq("testColl"));

        jobService.startJobExecution(job.getId());

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertFalse(job.getLastErrors().isEmpty());
        assertTrue(job.getLastErrors().get(0).contains("Connection lost"));
    }

    // validate schema test
    @Test
    void validateSchema_withInvalidSchema_shouldThrowException() {

        String schema = """
            {
                "type": "object",
                "properties": {
                    "age": {
                    "type": "invalidtype"
                    }
                }
            }
            """;

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSchemaValidator.validateSchema(schema)
        );
    }

    @Test
    void validateSchema_withInvalidJson_shouldThrowException() {

        String schema = """
            {
            "type": "object",
            "properties": {
                "name": { "type": "string" }
            """;

        assertThrows(
                Exception.class,
                () -> JsonSchemaValidator.validateSchema(schema)
        );
    }

    @Test
    void validateSchema_withNonSchemaJson_shouldThrowException() {

        String schema = "{ invalid json";

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSchemaValidator.validateSchema(schema)
        );
    }
}
