# Zippy Backend - Comprehensive Project Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture & Flow](#architecture--flow)
3. [Schema / Data Models](#schema--data-models)
4. [API Endpoints](#api-endpoints)
5. [Configuration & Deployment](#configuration--deployment)
6. [Refresh Token Persistence Solution](#refresh-token-persistence-solution)
7. [MQTT Broker Deployment](#mqtt-broker-deployment)
8. [Example Usage](#example-usage)
9. [Diagram Recommendations](#diagram-recommendations)

---

## 1. Project Overview

### Purpose
Zippy Backend is a **robotic delivery management system** that facilitates automated package delivery using autonomous robots. The system manages robot fleets, handles delivery orders, tracks trips, and provides real-time monitoring through MQTT communication.

### Key Features
- **User Authentication & Authorization** with JWT tokens and refresh token persistence
- **Robot Fleet Management** with real-time location and status tracking
- **Order Management** with automated trip assignment
- **MQTT-based Real-time Communication** for robot control and monitoring
- **QR Code Generation** for package identification
- **Email Notifications** for order updates
- **Redis Caching** for performance optimization
- **Hybrid Token Storage** (Redis + MySQL) for reliability

### Technologies Used
- **Backend Framework**: Spring Boot 3.x
- **Database**: MySQL 8.x with Hibernate/JPA
- **Caching**: Redis 6.x
- **Message Broker**: MQTT (Eclipse Mosquitto)
- **Authentication**: JWT with refresh token rotation
- **Email Service**: SMTP integration
- **Build Tool**: Maven
- **Java Version**: 17+

---

## 2. Architecture & Flow

### System Architecture
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client Apps   │    │   Robot Fleet   │    │  Admin Portal   │
│  (Mobile/Web)   │    │                 │    │                 │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          │ REST API             │ MQTT                 │ REST API
          │                      │                      │
    ┌─────▼──────────────────────▼──────────────────────▼─────┐
    │                 Spring Boot Application                 │
    │                                                         │
    │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
    │ │ Controllers │ │  Services   │ │   Config    │        │
    │ │   Layer     │ │   Layer     │ │   Layer     │        │
    │ └─────────────┘ └─────────────┘ └─────────────┘        │
    │                                                         │
    │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
    │ │ Repository  │ │    MQTT     │ │    Redis    │        │
    │ │   Layer     │ │  Publisher/ │ │   Cache     │        │
    │ │             │ │ Subscriber  │ │             │        │
    │ └─────────────┘ └─────────────┘ └─────────────┘        │
    └─────────┬───────────────┬───────────────┬───────────────┘
              │               │               │
    ┌─────────▼───────┐ ┌─────▼─────┐ ┌───────▼───────┐
    │ MySQL Database  │ │MQTT Broker│ │ Redis Server  │
    │                 │ │(Mosquitto)│ │               │
    └─────────────────┘ └───────────┘ └───────────────┘
```

### Request Flow
1. **Authentication Flow**:
   - Client sends login request → AuthController
   - AuthController validates credentials via UserService
   - JwtService generates access token and refresh token
   - TokenService stores refresh token in both Redis (performance) and MySQL (persistence)
   - Response with tokens sent back to client

2. **Robot Control Flow**:
   - Admin sends robot command → RobotController
   - RobotController publishes MQTT message via MqttCommandPublisher
   - Robot receives command and updates status
   - Robot publishes status updates via MQTT
   - MqttMessageSubscriber receives updates and updates database

3. **Order Processing Flow**:
   - Client creates order → OrderController
   - OrderService assigns available robot and creates trip
   - Robot receives trip command via MQTT
   - Real-time updates flow through MQTT → Database → Client notifications

---

## 3. Schema / Data Models

### Core Entities

#### User Entity
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    status ENUM('ACTIVE', 'PENDING', 'DISABLED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### RefreshToken Entity (NEW - Solves persistence issue)
```sql
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(500) UNIQUE NOT NULL,
    username VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    device_info VARCHAR(255),
    INDEX idx_token (token),
    INDEX idx_username (username),
    INDEX idx_expires_at (expires_at)
);
```

#### Robot Entity
```sql
CREATE TABLE robots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    robot_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255),
    status ENUM('AVAILABLE', 'BUSY', 'MAINTENANCE', 'OFFLINE') DEFAULT 'OFFLINE',
    battery_level INTEGER DEFAULT 0,
    location_x DOUBLE,
    location_y DOUBLE,
    current_trip_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Order Entity
```sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    pickup_location VARCHAR(500),
    delivery_location VARCHAR(500),
    status ENUM('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'DELIVERED', 'CANCELLED'),
    total_amount DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

#### Trip Entity
```sql
CREATE TABLE trips (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    robot_id BIGINT NOT NULL,
    status ENUM('CREATED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (robot_id) REFERENCES robots(id)
);
```

---

## 4. API Endpoints

### Authentication Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/auth/login` | User login | `LoginRequest` | `LoginResponse` with tokens |
| POST | `/api/auth/register` | User registration | `RegisterRequest` | `RegisterResponse` |
| POST | `/api/auth/refresh-token` | Refresh access token | `RefreshTokenRequest` | New tokens |
| POST | `/api/auth/verify` | Email verification | `VerifyRequest` | `VerifyResponse` |
| POST | `/api/auth/logout` | User logout | - | Success message |

### Robot Management Endpoints

| Method | Endpoint | Description | Parameters | Response |
|--------|----------|-------------|------------|----------|
| GET | `/api/robots` | Get all robots | - | List of robots |
| GET | `/api/robots/{id}` | Get robot by ID | `id: Long` | Robot details |
| POST | `/api/robots/{robotId}/command/move` | Send move command | `robotId, coordinates` | Command status |
| GET | `/api/robots/{robotId}/status` | Get robot status | `robotId: String` | Robot status |

### Order Management Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/orders` | Create new order | `OrderRequest` | Created order |
| GET | `/api/orders` | Get user orders | - | List of orders |
| GET | `/api/orders/{id}` | Get order details | - | Order details |
| PUT | `/api/orders/{id}/cancel` | Cancel order | - | Updated order |

### Trip Management Endpoints

| Method | Endpoint | Description | Parameters | Response |
|--------|----------|-------------|------------|----------|
| GET | `/api/trips` | Get all trips | - | List of trips |
| GET | `/api/trips/{id}` | Get trip details | `id: Long` | Trip details |
| POST | `/api/trips/{id}/start` | Start trip | `id: Long` | Updated trip |

### MQTT Topics

#### Inbound Topics (Robot → Server)
- `robot/+/location` - Robot location updates
- `robot/+/battery` - Battery level updates  
- `robot/+/status` - Robot status updates
- `robot/+/container/+/status` - Container status
- `robot/+/trip/+` - Trip progress updates

#### Outbound Topics (Server → Robot)
- `robot/+/command/move` - Movement commands
- `robot/+/command/request-status` - Status requests
- `robot/+/container/+/command/trip/+/load` - Load commands
- `robot/+/container/+/command/trip/+/pickup` - Pickup commands
- `robot/+/command/trip/+/move` - Trip movement commands
- `robot/+/command/trip/+/continue` - Continue trip commands

---

## 5. Configuration & Deployment

### Environment Variables
```yaml
# Database Configuration
DB_URL=jdbc:mysql://localhost:3306/zippy
DB_USERNAME=root
DB_PASSWORD=12345678

# Redis Configuration  
REDIS_HOST=localhost
REDIS_PORT=6379

# MQTT Configuration
MQTT_BROKER=tcp://localhost:1883
MQTT_USERNAME=khanhnc
MQTT_PASSWORD=12345678

# JWT Configuration
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_ACCESS_EXPIRATION=45000
JWT_REFRESH_EXPIRATION=604800000

# Email Configuration
MAIL_HOST=smtp.phamanh.io.vn
MAIL_PORT=465
MAIL_USERNAME=zippy@phamanh.io.vn
MAIL_PASSWORD=TiinJzRyX4Aqr0
```

### Docker Deployment
```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/zippy-backend-*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### Docker Compose
```yaml
version: '3.8'
services:
  zippy-backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DB_URL=jdbc:mysql://mysql:3306/zippy
      - REDIS_HOST=redis
      - MQTT_BROKER=tcp://mosquitto:1883
    depends_on:
      - mysql
      - redis
      - mosquitto

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: zippy
      MYSQL_ROOT_PASSWORD: 12345678
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  mosquitto:
    image: eclipse-mosquitto:2.0
    ports:
      - "1883:1883"
      - "9001:9001"
    volumes:
      - ./mosquitto.conf:/mosquitto/config/mosquitto.conf

volumes:
  mysql_data:
```

---

## 6. Refresh Token Persistence Solution

### Problem
Your refresh tokens were previously stored only in Redis (in-memory), causing all active sessions to be lost when the application restarts.

### Solution: Hybrid Storage Approach

#### Implementation Details
1. **Dual Storage**: Refresh tokens are now stored in both Redis (for performance) and MySQL (for persistence)
2. **Automatic Sync**: On application startup, active tokens from database are synced to Redis
3. **Fallback Mechanism**: If token not found in Redis, system checks database and re-syncs to Redis
4. **Cleanup Service**: Scheduled tasks remove expired tokens from both storage systems

#### Key Benefits
- **Persistence**: Tokens survive application restarts
- **Performance**: Redis provides fast token validation
- **Reliability**: Database ensures data durability
- **Security**: Proper token revocation across both systems

#### Database Schema
```sql
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(500) UNIQUE NOT NULL,
    username VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    device_info VARCHAR(255)
);
```

#### Service Implementation
- **TokenService**: Enhanced to support hybrid storage
- **RefreshTokenRepository**: JPA repository for database operations
- **TokenCleanupService**: Scheduled cleanup of expired tokens

---

## 7. MQTT Broker Deployment

### Current Local Setup
```bash
CONTAINER_ID="1b3"
MQTT_HOST="localhost" 
MQTT_PORT=1883
MQTT_USER="khanhnc"
MQTT_PASS="12345678"
```

### Cloud Deployment Recommendations

#### Option 1: Self-Hosted on VPS/Cloud
```yaml
# docker-compose.yml for cloud deployment
version: '3.8'
services:
  mosquitto:
    image: eclipse-mosquitto:2.0
    container_name: zippy-mqtt
    ports:
      - "1883:1883"
      - "9001:9001"  # WebSocket
      - "8883:8883"  # SSL/TLS
    volumes:
      - ./mosquitto/config:/mosquitto/config
      - ./mosquitto/data:/mosquitto/data
      - ./mosquitto/log:/mosquitto/log
      - ./ssl:/mosquitto/ssl
    restart: unless-stopped
    environment:
      - MOSQUITTO_USERNAME=${MQTT_USER}
      - MOSQUITTO_PASSWORD=${MQTT_PASS}
```

**Mosquitto Configuration for Production:**
```conf
# mosquitto.conf
listener 1883
allow_anonymous false
password_file /mosquitto/config/passwd

listener 9001
protocol websockets
allow_anonymous false

listener 8883
cafile /mosquitto/ssl/ca.crt
certfile /mosquitto/ssl/server.crt
keyfile /mosquitto/ssl/server.key
require_certificate false
```

#### Option 2: AWS IoT Core
```yaml
# application-prod.yaml
mqtt:
  broker: ssl://your-endpoint.iot.region.amazonaws.com:8883
  client-id: zippy-backend-prod
  ssl:
    enabled: true
    certificate-path: /etc/ssl/certs/device.pem
    private-key-path: /etc/ssl/private/private.key
    ca-certificate-path: /etc/ssl/certs/AmazonRootCA1.pem
```

#### Option 3: HiveMQ Cloud (Managed)
```yaml
mqtt:
  broker: ssl://your-cluster.s1.eu.hivemq.cloud:8883
  client-id: zippy-backend-prod
  username: ${HIVEMQ_USERNAME}
  password: ${HIVEMQ_PASSWORD}
  ssl:
    enabled: true
```

#### Option 4: Google Cloud IoT Core
```yaml
mqtt:
  broker: ssl://mqtt.googleapis.com:8883
  client-id: projects/PROJECT_ID/locations/REGION/registries/REGISTRY_ID/devices/DEVICE_ID
  jwt-token: ${GOOGLE_IOT_JWT_TOKEN}
```

### Recommended Production Setup
For your application, I recommend **AWS IoT Core** or **self-hosted Mosquitto on cloud VPS** because:

1. **AWS IoT Core**: 
   - Built-in security and authentication
   - Automatic scaling
   - Integration with other AWS services
   - Device management features

2. **Self-hosted Mosquitto**:
   - Full control over configuration
   - Cost-effective for high message volumes
   - Custom authentication integration
   - Easy to migrate from local setup

### Cloud Configuration Updates
```yaml
# application-prod.yaml
spring:
  profiles: prod

mqtt:
  broker: ssl://your-mqtt-broker.com:8883
  client-id: zippy-backend-${random.uuid}
  username: ${MQTT_PROD_USER}
  password: ${MQTT_PROD_PASS}
  ssl:
    enabled: true
    trust-store: classpath:ssl/truststore.jks
    trust-store-password: ${SSL_TRUSTSTORE_PASSWORD}
  connection:
    timeout: 30000
    keep-alive: 60
    clean-session: false
    automatic-reconnect: true
```

---

## 8. Example Usage

### Authentication Flow Example
```bash
# 1. User Registration
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "confirmPassword": "password123",
    "fullName": "John Doe"
  }'

# 2. Email Verification
curl -X POST http://localhost:8080/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "otp": "123456"
  }'

# 3. User Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credential": "user@example.com",
    "password": "password123"
  }'

# Response:
{
  "success": true,
  "message": "User logged in successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "verificationRequired": false
  }
}

# 4. Refresh Token Usage
curl -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }'
```

### Robot Control Example
```bash
# Get all robots
curl -X GET http://localhost:8080/api/robots \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."

# Send move command to robot
curl -X POST http://localhost:8080/api/robots/robot-001/command/move \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "targetX": 10.5,
    "targetY": 20.3,
    "priority": "HIGH"
  }'
```

### Order Creation Example
```bash
# Create new order
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "pickupLocation": "Building A, Floor 1",
    "deliveryLocation": "Building B, Floor 3",
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ]
  }'
```

### MQTT Message Examples
```javascript
// Robot location update (Robot → Server)
Topic: robot/robot-001/location
Payload: {
  "robotId": "robot-001",
  "x": 15.2,
  "y": 25.8,
  "timestamp": "2025-08-21T10:30:00Z"
}

// Move command (Server → Robot)
Topic: robot/robot-001/command/move
Payload: {
  "targetX": 20.0,
  "targetY": 30.0,
  "speed": "NORMAL",
  "commandId": "cmd-123456"
}
```

---

## 9. Diagram Recommendations

### System Architecture Diagram
Create a high-level system architecture diagram showing:
- Client applications (mobile/web)
- Spring Boot backend
- Database layer (MySQL)
- Cache layer (Redis)
- Message broker (MQTT)
- Robot fleet
- External services (email)

### Database Schema ERD
Create an Entity Relationship Diagram including:
- User, Role, RefreshToken entities
- Robot, RobotContainer entities  
- Order, Trip, Payment entities
- Product entity
- Relationships and foreign keys

### Authentication Sequence Diagram
Show the complete authentication flow:
1. Login request
2. Credential validation
3. Token generation (access + refresh)
4. Hybrid storage (Redis + Database)
5. Token refresh process
6. Logout and token revocation

### MQTT Communication Flow
Illustrate bidirectional MQTT communication:
- Robot status updates
- Command distribution
- Topic routing
- Message persistence

### Request/Response Flow Diagram
Detail the typical request lifecycle:
1. Client request with JWT
2. Authentication filter validation
3. Controller processing
4. Service layer execution
5. Database operations
6. Response formation
7. Caching mechanisms

---

## Deployment Checklist

### Before Deploying to Production:

#### Database Setup
- [ ] Configure production MySQL database
- [ ] Run database migrations
- [ ] Set up database backups
- [ ] Configure connection pooling

#### Redis Setup  
- [ ] Deploy Redis in production mode
- [ ] Configure Redis persistence (RDB + AOF)
- [ ] Set up Redis clustering if needed
- [ ] Configure memory limits

#### MQTT Broker Setup
- [ ] Choose MQTT deployment strategy (self-hosted vs managed)
- [ ] Configure SSL/TLS certificates
- [ ] Set up proper authentication
- [ ] Configure topic permissions
- [ ] Test connectivity from application

#### Security
- [ ] Change default JWT secret key
- [ ] Set up proper CORS configuration
- [ ] Configure rate limiting
- [ ] Set up monitoring and alerting
- [ ] Review and update all default passwords

#### Application Configuration
- [ ] Set production profiles
- [ ] Configure environment variables
- [ ] Set up logging configuration
- [ ] Configure health checks
- [ ] Set up monitoring endpoints

This comprehensive documentation provides a complete technical overview of your Zippy Backend system, addresses the refresh token persistence issue with a production-ready solution, and includes detailed recommendations for MQTT broker deployment in cloud environments.
