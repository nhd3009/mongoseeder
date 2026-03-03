# Mongodb seeder webapp
## 📖 Overview
This project is a Spring Boot 3 backend application that generates fake JSON data based on a user-defined JSON Schema, then inserts it directly into MongoDB using multiple threads.
The system supports:
- Creating and managing data generation jobs.
- Generating data in parallel using configurable threads.
- Inserting data in batches or single documents.
- Monitoring job progress, throughput, and errors in real-time.
> Note: I use GraalVM's JavaScript engine to execute json-schema-faker code from Java.
> This allows our Spring Boot backend to generate complex fake data (including nested objects and arrays) based on the JSON Schema input.
## 🚀 Features
Create a job from a given JSON schema and configuration
- ```POST /api/jobs```

Start a job (generate and insert data)
- ```POST /api/jobs/{jobId}/start```

Stop a running job gracefully
- ```POST /api/jobs/{jobId}/stop```

Get job status and metrics
- ```GET /api/jobs/{jobId}```

Stream live metrics via SSE
- ```GET /api/jobs/{jobId}/stream```

## Data Generation
- Data generation is powered by **[json-schema-faker](https://github.com/json-schema-faker/json-schema-faker)** (JS library).
- Support for nested objects and arrays.
- Configurable number of records (N), threads (T), and batch size.

## Multithreading & Performance

- Parallel insertion using ExecutorService.

- Graceful stop support — ongoing tasks will finish cleanly.

- Robust error handling (e.g. MongoDB disconnection triggers job failure).

## Metrics Tracked

- startTime, endTime

- totalRecords, insertedRecords

- batchSize, numThreads

- recordsPerSecond

- errorCount, lastErrors[]

## Tech Stack

- Spring Boot 3.x ( I use 3.5.9)
- Spring Data MongoDB
- ExecutorService for multithreading
- SLF4J + Logback for logging
- GraalVM

## Prerequisites
- Java 21
- Maven
- MongoDB

## Configuration
All MongoDB configurations are provided through the job request payload.
Alternatively, you can configure MongoDB connection in application.yml for default use. You can create the .env variable
```
spring:
  data:
    mongodb:
      uri: ${MONGODB_URL}/${DATABASE_NAME}
```

## Docker
Following the step
1. Create docker.env in root directory
```
MONGODB_URL=mongodb://mongodb:27017
DATABASE_NAME=seeddb_docker
SPRING_PROFILES_ACTIVE=docker
```
2. Build docker image
```
docker compose build --no-cache
```
3. Start service
```
docker compose up -d
```
4. Check container
```
docker ps
```

## API Example
1. Create a job
   - Request
   ```
   POST /api/jobs
   Content-Type: multipart/form-data
   "collectionName": "users"
   "totalRecords": 100000
   "threadCount": 8,
   "batchSize": 2000
   "schemaJson": "{
    	"type": "object", 
    	"properties": {
    		"name": {
    			"type": "string",
    			"faker": "name.findName"
    		},
    		"email": {
    			"type": "string",
    			"faker": "internet.email"
    		}
    	},
    	"required": ["name", "email"]
    }"
   "databaseName": "seeddb_test"
   ```
   - Response example
    ```
    {
    	"id": "561a914e-6235-497b-8bc0-d056b84f8b40",
    	"config": {
    		"schemaJson": "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"faker\": \"name.findName\"}, \"email\": {\"type\": \"string\", \"faker\": \"internet.email\"}}, \"required\": [\"name\", \"email\"]}",
    		"collectionName": "users",
    		"totalRecords": 100000,
    		"threadCount": 8,
    		"batchSize": 2000
    	},
    	"status": "PENDING",
    	"metrics": {
    		"startTime": 0,
    		"endTime": null,
    		"insertedRecords": 0,
    		"failedRecords": 0,
    		"errorCount": 0,
    		"recordsPerSecond": 0.0
    	},
    	"stopRequested": false,
    	"lastErrors": []
    }
    ```
3. Start a job
   ```
   POST /api/jobs/{jobId}/start
   ```
4. Stop a job
   ```
   POST /api/jobs/{jobId}/stop
   ```
5. GEt a job status
   ```
   POST /api/jobs/{jobId}
   ```
   - Response
    ```
    {
    	"id": "b8059fe5-1044-4a84-8ec6-6c66d74933dc",
    	"config": {
    		"schemaJson": "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"faker\": \"name.findName\"}, \"email\": {\"type\": \"string\", \"faker\": \"internet.email\"}}, \"required\": [\"name\", \"email\"]}",
    		"collectionName": "users",
    		"totalRecords": 100000,
    		"threadCount": 8,
    		"batchSize": 2000
    	},
    	"status": "COMPLETED",
    	"metrics": {
    		"startTime": 1767584601585,
    		"endTime": 1767584635270,
    		"insertedRecords": 100000,
    		"failedRecords": 0,
    		"errorCount": 0,
    		"recordsPerSecond": 3030.3030303030305
    	},
    	"stopRequested": false,
    	"lastErrors": []
    }
    ```
6. SSE Realtime log tracking job status
- via Terminal
  ```
  curl -N http://localhost:8080/api/jobs/{jobId}/stream
  ```
- via Insomnia/Postman
  Create a Event Stream REquest
  ```
  GET /api/jobs/{jobId}/stream
  ```
- Response Example (via Terminal)
```
data:{"id":"561a914e-6235-497b-8bc0-d056b84f8b40","config":{"schemaJson":"{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"faker\": \"name.findName\"}, \"email\": {\"type\": \"string\", \"faker\": \"internet.email\"}}, \"required\": [\"name\", \"email\"]}","collectionName":"users","totalRecords":100000,"threadCount":8,"batchSize":2000},"status":"COMPLETED","metrics":{"startTime":1767586456825,"endTime":1767586494754,"insertedRecords":100000,"failedRecords":0,"errorCount":0,"recordsPerSecond":2702.7027027027025},"stopRequested":false,"lastErrors":[]}
```

## Credit
**[@nhd3009](https://github.com/nhd3009)**
</br>
**Hope you like it and give it a star :3**
