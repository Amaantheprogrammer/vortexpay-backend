# Digital Banking System

An enterprise-grade Digital Banking System built using Spring Boot, Spring Security, MySQL, Redis, and Docker. The application provides secure user authentication, account management, transaction processing, fraud monitoring, audit logging, and role-based access control through RESTful APIs.

---

## Features

### Authentication & Authorization

* JWT-based authentication and authorization
* Secure user registration and login
* Logout with Redis-backed token blacklisting
* Role-Based Access Control (RBAC) with ADMIN and USER roles
* BCrypt password hashing
* Protected endpoints using Spring Security

### User Management

* User registration and onboarding
* Profile retrieval and updates
* Fetch users by ID or email
* Secure access to authenticated user information

### Account Management

* Create and manage bank accounts
* Multiple accounts per user
* Retrieve account details and balances
* Account status management
* Account type support
* Account retrieval optimization using Redis caching

### Transaction Management

* Money transfers between accounts
* Deposit funds
* Withdraw funds
* Transaction history retrieval
* Unique transaction reference tracking
* ACID-compliant transaction processing
* Idempotent transaction execution to prevent duplicate requests

### Fraud Monitoring

* Fraud log generation
* Suspicious transaction detection
* Fraud status tracking
* Administrative fraud review workflows
* Fraud management endpoints

### Security

* Spring Security integration
* JWT token validation
* Custom JWT authentication filter
* Role-based endpoint authorization
* Token revocation through Redis
* Secure financial transaction processing

### Audit Logging

* AOP-based audit trail implementation
* Tracking of critical banking operations
* Persistent audit records
* Improved observability and compliance support

### Reliability & Performance

* Global exception handling
* Request validation
* Structured application logging using SLF4J
* Redis caching integration
* Reduced account retrieval latency by approximately 96% (700 ms → 28 ms)

### API Documentation

* Swagger/OpenAPI integration
* Interactive API testing
* Endpoint documentation for faster development and onboarding

### DevOps & Deployment

* Fully Dockerized application stack
* Docker Compose orchestration
* MySQL containerized deployment
* Redis containerized deployment
* Environment-independent setup and deployment

---

## Tech Stack

### Backend

* Java 17
* Spring Boot
* Spring Security
* Spring Data JPA
* Hibernate

### Database

* MySQL

### Caching & Session Management

* Redis

### Authentication

* JWT (JSON Web Token)

### API Documentation

* Swagger / OpenAPI

### DevOps

* Docker
* Docker Compose

### Utilities

* Lombok
* ModelMapper
* Jakarta Validation
* SLF4J Logging
* Spring AOP

---

## Project Structure

```text
src/main/java/com/MyProject/DigitalBankingSystem

├── account
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── auth
│   ├── controller
│   ├── dto
│   ├── jwt
│   ├── custom
│   └── service
│
├── transaction
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── fraud
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── idempotency
│   ├── service
│
├── audit
│   ├── aspect
│   ├── entity
│   ├── repository
│   └── service
│
├── user
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── config
├── exception
└── util
```

---

## Database Design

### User

Stores customer and administrator information.

| Field     | Type          |
| --------- | ------------- |
| id        | Long          |
| name      | String        |
| email     | String        |
| password  | String        |
| role      | Enum          |
| createdAt | LocalDateTime |

---

### Account

Represents a bank account owned by a user.

| Field         | Type          |
| ------------- | ------------- |
| id            | Long          |
| accountNumber | String        |
| balance       | BigDecimal    |
| status        | Enum          |
| accountType   | Enum          |
| createdAt     | LocalDateTime |

---

### Transaction

Represents transfers, deposits, and withdrawals.

| Field                | Type          |
| -------------------- | ------------- |
| id                   | Long          |
| transactionReference | String        |
| senderAccount        | Account       |
| receiverAccount      | Account       |
| amount               | BigDecimal    |
| transactionType      | Enum          |
| status               | Enum          |
| transactionAt        | LocalDateTime |

---

### FraudLog

Tracks suspicious transactions and fraud investigations.

| Field                | Type          |
| -------------------- | ------------- |
| id                   | Long          |
| accountNumber        | String        |
| transactionReference | String        |
| status               | Enum          |
| reason               | String        |
| detectedAt           | LocalDateTime |
| reviewedAt           | LocalDateTime |

---

## API Modules

### Authentication APIs

```http
POST /auth/register
POST /auth/login
POST /auth/logout
```

### User APIs

```http
GET /users/{userId}
GET /users/email/{email}
GET /users/my-user
PATCH /users/{userId}
```

### Account APIs

```http
POST /accounts
GET /accounts/{accountId}
GET /accounts/account-number/{accountNumber}
GET /accounts/user/{userId}
GET /accounts/my-account/{accountNumber}
GET /accounts/my-accounts
```

### Transaction APIs

```http
POST /transactions/transfer
POST /transactions/deposit
POST /transactions/withdraw

GET /transactions/{transactionId}
GET /transactions/reference/{transactionReference}
GET /transactions/all-transactions/{accountNumber}
```

### Fraud APIs

```http
GET /fraud-logs
GET /fraud-logs/{fraudLogId}
PATCH /fraud-logs/{fraudLogId}/status
```

---

## Dockerized Deployment

The application is fully containerized using Docker and Docker Compose.

### Services

| Service         | Purpose                      |
| --------------- | ---------------------------- |
| Spring Boot App | Banking API                  |
| MySQL           | Persistent Database          |
| Redis           | Token Blacklisting & Caching |

### Run Using Docker

```bash
docker-compose up --build
```

### Container Architecture

```text
┌──────────────────────┐
│   Spring Boot API    │
└──────────┬───────────┘
           │
     ┌─────┴─────┐
     │           │
┌────▼────┐ ┌────▼────┐
│ MySQL   │ │ Redis   │
└─────────┘ └─────────┘
```

---

## Getting Started

### Clone Repository

```bash
git clone https://github.com/Amaantheprogrammer/DigitalBankingSystem.git
cd DigitalBankingSystem
```

### Configure MySQL

```sql
CREATE DATABASE digital_banking_system;
```

Update database credentials inside:

```properties
application.properties
```

Example:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/digital_banking_system
spring.datasource.username=root
spring.datasource.password=your_password
```

### Configure Redis

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Run Application

```bash
./mvnw spring-boot:run
```

or

```bash
mvn spring-boot:run
```

---

## Security Flow

1. User registers.
2. User logs in.
3. JWT token is generated.
4. Client sends JWT in the Authorization header.
5. JWT filter validates the token.
6. Spring Security authorizes requests based on user roles.
7. Logout blacklists the token in Redis.
8. Blacklisted tokens are denied access to protected resources.

---

## Key Highlights

* Enterprise-grade layered architecture
* 25+ REST APIs
* JWT Authentication & RBAC
* Redis Token Blacklisting
* Fraud Detection & Monitoring
* AOP-Based Audit Logging
* Idempotency Support
* Dockerized Deployment
* Swagger/OpenAPI Documentation
* Global Exception Handling
* Redis Caching
* 96% Reduction in Account Retrieval Latency (700 ms → 28 ms)

---

## Future Enhancements

* Multi-Factor Authentication (MFA)
* Email & SMS Notifications
* Beneficiary Management
* Scheduled Transfers
* PDF Bank Statements
* Interest Calculation Engine
* Kafka-Based Event Streaming
* Real-Time Fraud Analytics
* CI/CD Pipeline with GitHub Actions
* Banking Analytics Dashboard

---

## Author

**Amaan Coatwala**

Backend Developer focused on Java, Spring Boot, Distributed Systems, and Enterprise Application Development.
