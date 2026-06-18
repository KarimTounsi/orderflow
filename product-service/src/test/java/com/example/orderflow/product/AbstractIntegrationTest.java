package com.example.orderflow.product;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.ai.model.chat=none")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @ServiceConnection
    @SuppressWarnings("resource")
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        mongoDBContainer.start();
        redisContainer.start();
        postgresContainer.start();
    }
}
