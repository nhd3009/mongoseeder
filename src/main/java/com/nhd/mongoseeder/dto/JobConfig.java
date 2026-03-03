package com.nhd.mongoseeder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobConfig {
    @NotBlank(message = "Json Schema is not blank")
    private String schemaJson;

    @NotBlank(message = "Database name is not blank")
    private String databaseName;

    @NotBlank(message = "Collection name is not blank")
    private String collectionName;

    @Min(value = 1, message = "Total records must be > 0")
    private int totalRecords;

    @Min(value = 1, message = "Thread count must be >= 1")
    private int threadCount = 4;

    @Min(value = 1, message = "Batch size must be >= 1")
    private int batchSize = 1000;
}

