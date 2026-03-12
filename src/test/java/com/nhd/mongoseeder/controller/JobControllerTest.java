package com.nhd.mongoseeder.controller;

import com.nhd.mongoseeder.dto.JobConfig;
import com.nhd.mongoseeder.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @Test
    void createJob_withInvalidInput_shouldReturnBadRequest() throws Exception {

        doThrow(new IllegalArgumentException("Invalid JSON schema"))
            .when(jobService).createJob(any(JobConfig.class));

        mockMvc.perform(multipart("/api/jobs")
                .param("schemaJson", "")
                .param("databaseName", "hello")
                .param("collectionName", "test")
                .param("totalRecords", "1")
                .param("threadCount", "1")
                .param("batchSize", "1"))
                .andExpect(status().isBadRequest());
    }
}
