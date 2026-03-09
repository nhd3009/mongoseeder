package com.nhd.mongoseeder.config;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;

@Component
public class MongoTemplateFactory {

    private final MongoClient mongoClient;

    public MongoTemplateFactory(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public MongoTemplate create(String databaseName) {
        return new MongoTemplate(mongoClient, databaseName);
    }
}
