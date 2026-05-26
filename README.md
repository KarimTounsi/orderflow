# OrderFlow

E-commerce backend demo showcasing event-driven microservices architecture with four core technologies: **Kafka**, **Redis**, **MongoDB**, and **PostgreSQL** - deployed on **AWS EC2**.

[![Coverage](https://img.shields.io/badge/coverage-80%25-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-25-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green)]()

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          AWS EC2                                  │
│                                                                   │
│  ┌─────────────────┐   ┌─────────────────┐   ┌───────────────┐  │
│  │ product-service │   │  order-service  │   │ fulfillment-  │  │
│  │                 │   │                 │   │    service    │  │
│  │  MongoDB        │   │  PostgreSQL     │   │               │  │
│  │  Redis cache    │   │  Kafka producer │   │ Kafka consumer│  │
│  └────────┬────────┘   └────────┬────────┘   └───────┬───────┘  │
│           │                     │                     │          │
│           └─────────────────────▼─────────────────────┘          │
│                       Confluent Cloud (Kafka)                     │
└─────────────────────────────────────────────────────────────────┘
```

**Flow:** Customer browses products → adds to cart → places order  
→ `order-placed` event published to Kafka  
→ fulfillment-service consumes event → sends confirmation email

## Services

| Service | Port | Technologies | Responsibility |
|---------|------|-------------|----------------|
| product-service | 8081 | MongoDB, Redis | Product catalog, shopping cart |
| order-service | 8082 | PostgreSQL, Kafka | Order placement, event publishing |
| fulfillment-service | 8083 | Kafka, SES | Order processing, email confirmation |

## Tech Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| Messaging | Apache Kafka | Async event-driven communication between services |
| Cache | Redis | Product catalog cache (TTL 5min), shopping cart session |
| Document DB | MongoDB | Flexible product schema with variants and attributes |
| Relational DB | PostgreSQL / Neon | ACID transactions for orders and payments |
| Infrastructure | AWS EC2 + Docker Compose | Containerized deployment |
| CI/CD | GitHub Actions | Automated build and test on every push |

## Running Locally

**Prerequisites:** Docker, Java 25, Maven

```bash
# Start infrastructure (Kafka, Redis, MongoDB, PostgreSQL, Kafka UI)
docker compose up -d

# Start product-service
./mvnw spring-boot:run -pl product-service

# Start order-service
./mvnw spring-boot:run -pl order-service

# Start fulfillment-service
./mvnw spring-boot:run -pl fulfillment-service

# Run all tests with coverage
./mvnw verify

# View Kafka topics and messages
open http://localhost:8090
```

## What the Demo Shows

| Action | What happens under the hood |
|--------|----------------------------|
| Browse products | MongoDB query - Redis cache (second request is 10x faster) |
| Add to cart | Cart stored in Redis with 30min TTL |
| Place order | Saved to PostgreSQL - `order-placed` event published to Kafka |
| Receive confirmation email | fulfillment-service consumes Kafka event - sends email |
| Kafka UI at :8090 | Real-time view of events flowing between services |

## Infrastructure

- **AWS EC2** - hosts all three services via Docker Compose
- **Confluent Cloud** - managed Kafka
- **MongoDB Atlas** - managed MongoDB
- **Neon** - serverless PostgreSQL
- **Upstash** - managed Redis
- **Vercel** - Next.js frontend
- **SES** - transactional email

## Tests

Each service has unit tests (JUnit 5 + Mockito) and integration tests (Testcontainers).  
JaCoCo enforces minimum **80% line coverage** on `./mvnw verify`.
