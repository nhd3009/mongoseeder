package com.nhd.mongoseeder;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootTest
@TestConfiguration
@EnableAsync
class MongoseederApplicationTests {

    @Test
    void contextLoads() {
    }

    @Bean
    public Executor taskExecutor() {
        return Runnable::run;
    }

}
