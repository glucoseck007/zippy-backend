# Zippy Backend – Project Documentation

## 1) Project Overview

Purpose and features
- Manages robot-assisted delivery trips and orders:
  - User registration, OTP email verification, login/logout, refresh tokens.
  - User profile view/edit.
  - Order creation and approval; QR code generation and MQTT delivery to robots.
  - Trip lifecycle and progress tracking (via robot MQTT updates and Redis caching).
  - Robot telemetry ingestion via MQTT (location, battery, status, container status) and cache-backed querying.
  - Robot command dispatch via MQTT (move, load, pickup, trip-continue; trip-scoped variants).
  - Pickup OTP flow for order completion.
- Error handling via a centralized exception advisor returning consistent ApiResponse envelopes.

Primary technologies
- Java, Spring Boot (REST, scheduling), Spring MVC, Spring Security (JWT), Spring Data JPA (MySQL), Spring Integration + Eclipse Paho (MQTT), Redis (Lettuce), JavaMail, ZXing (QR).
- JWT: jjwt.
- Build: Maven.

## 2) Architecture & Flow

Layers and modules
- Web layer (controllers):
  - auth: AuthController
  - account: AccountController
  - order: OrderController
  - trip: TripController
  - robot: RobotStatusController (direct cache DTOs), RobotMessageController (wrapped responses), RobotCommandController (MQTT command endpoints)
- Service layer:
  - Auth: JwtService, TokenService (Redis-backed refresh + blacklist), UserService, OtpService, CustomUserDetailsService
  - Order: OrderService, PickupOtpService, OrderCodeGenerator
  - Trip: TripService, TripStatusService, TripCodeGenerator
  - Robot: RobotMessageService (ingest MQTT), RobotDataService (cache-first + DB persistence), RobotStateCache (in-memory)
  - MQTT: MqttSubscriberImpl (direct Paho subscriber)
  - QR/Email: QRCodeService, EmailService/EmailServiceImpl
- Persistence layer (Spring Data repositories): User, Role, Robot, RobotContainer, Trip, Product, Order, Payment repositories
- Config:
  - SecurityConfig + JwtAuthenticationFilter
  - DatabaseConfig (JPA auditing), DataLoader (optional dummy data), RedisConfig + EmbeddedRedisConfig, MqttConfig, JwtConfig, MqttProperties

Core flows
- Authentication
  - /api/auth/register: create user with role USER, status PENDING → send OTP email.
  - /api/auth/verify-otp: verify OTP (by credential: username or email), set user ACTIVE.
  - /api/auth/login: authenticate; revoke all user refresh tokens; issue JWT access token (role claim) and new refresh token (stored in Redis).
  - /api/auth/logout: blacklist access token (Redis).
  - /api/auth/refresh-token: validate refresh token (Redis), revoke it, issue new access and refresh tokens.
  - Security filter: JwtAuthenticationFilter validates access token and populates SecurityContext; all non-/api/auth/** endpoints require Bearer token.
- Robot telemetry ingestion (MQTT)
  - MqttSubscriberImpl connects to broker and subscribes to:
    - robot/+/location, robot/+/battery, robot/+/status, robot/+/container/+/status, robot/+/trip/+
  - It parses topics and delegates to RobotMessageService, which:
    - Parses payload JSON to DTOs and updates RobotDataService:
      - Cache update in RobotStateCache (in-memory, with periodic cleanup).
      - Marks robot online and persists to DB asynchronously:
        - Robot.locationRealtime (lat,lon string) and batteryStatus fields.
        - RobotContainer.status from container updates.
    - TripStatusService handles robot/+/trip/+ payloads (JSON with progress), caches progress in Redis, and updates Trip.status (PENDING/ACTIVE/DELIVERED by rules). When trip becomes DELIVERED, associated orders are set to DELIVERED.
- Robot commands (via REST → MQTT publish)
  - RobotCommandController calls RobotCommandService, which builds topic strings and JSON payloads and publishes via MqttCommandPublisher (Spring Integration’s mqttOutboundChannel).
  - Commands: move, load, pickup, request-status, trip-scoped move/load/pickup/continue.
- Orders and trips
  - createOrder:
    - Validates robot online + free; container free (from cache).
    - Finds or creates ACTIVE/PENDING Trip for robot (tripCode generated, robot UUID looked up).
    - Persists Product (container bound) and Order (PENDING).
  - Approval and QR:
    - approveOrder: sets order to APPROVED.
    - generate-qr: creates a Base64 PNG QR from {orderCode, tripCode, timestamp} and publishes to robot/{robotCode}/command/qr.
  - Pickup OTP:
    - send-otp: validates order is DELIVERED, generates OTP keyed by orderCode, emails OTP to user.
    - verify-otp: validates OTP; completes order/trip (COMPLETED) and/or calls TripStatusService.completeTripByOtpVerification.

Notes
- Two inbound MQTT approaches exist:
  - MqttSubscriberImpl (in use).
  - MqttConfig with inbound adapter + handler that logs and TODO; outbound channel is used for publishing.
- Robot data reading endpoints:
  - RobotStatusController returns raw DTOs from RobotStateCache (not ApiResponse).
  - RobotMessageController wraps in ApiResponse and adds connection info (from RobotDataService last-seen).

## 3) Schema / Data Models

Database: MySQL 8 (hibernate ddl-auto: update). IDs are UUID for most entities; Robot uses binary(16).

Entities and relationships
- Role (role)
  - id (Long, PK), roleName (unique)
  - 1..n Users
- User (user)
  - id (UUID, PK), firstName, lastName, email (unique), phone, address, username (unique), passwordHash, createdAt, updatedAt, status ("PENDING", "ACTIVE"), roleId (FK)
  - Many-to-one Role (roleId)
  - 1..n Trips, 1..n Payments
- Robot (robot)
  - id (UUID, PK, binary(16)), code (string), batteryStatus (string), locationRealtime (string "lat,lon")
  - 1..n Trips, 1..n RobotContainers
- RobotContainer (robot_container)
  - id (Long, PK), robotId (UUID FK), containerCode (unique), status ("free", "non-free")
  - Many-to-one Robot (robotId)
  - 1..n Products
- Trip (trip)
  - id (UUID, PK), tripCode (unique), startPoint, endPoint, userId (UUID FK), robotId (UUID FK), status ("PENDING", "ACTIVE", "DELIVERED", "COMPLETED"), startTime, endTime
  - Many-to-one User, Many-to-one Robot
  - 1..n Orders, 1..n Products
- Product (product)
  - id (UUID, PK), code, tripId (UUID FK), containerCode (FK to robot_container.container_code)
  - Many-to-one Trip, Many-to-one RobotContainer
- Order (orders)
  - id (UUID, PK), orderCode (unique), userId (UUID FK), tripId (UUID FK), productId (UUID FK), price (BigDecimal), createdAt, completedAt, updatedAt, status ("PENDING", "APPROVED", "DELIVERED", "COMPLETED", "FINISHED")
  - Many-to-one Trip
  - 1..n Payments
- Payment (payment)
  - id (UUID, PK), orderId, userId, transactedAt, paymentMethod, description, price, currency, createdAt, updatedAt, providerTransactionId
  - Many-to-one Order, Many-to-one User

In-memory/Redis data
- RobotStateCache (in-memory, auto-cleaned every 30s):
  - Maps for location (RobotLocationDTO), battery (RobotBatteryDTO), status (RobotStatusDTO), container status per robot:container key.
- Redis:
  - Refresh tokens: key "refresh_token:{uuid}" → username; set "user:tokens:{username}" → members of refresh token UUIDs.
  - Blacklisted access tokens: "blacklisted_token:{jwt}" with TTL (~15 min default).
  - Trip progress: "trip:progress:{tripCode}" → string double, TTL 24h.

DTOs (selected)
- ApiResponse<T>: { success, message, data, timestamp }
- RobotLocationDTO { lat, lon, roomCode }, RobotBatteryDTO { battery }, RobotStatusDTO { robotCode, status }, RobotContainerStatusDTO { status }
- Auth
  - LoginRequest { credential, password, rememberMe?, deviceInfo?, ipAddress?, twoFactorCode? }
  - RegisterRequest { firstName, lastName, email, phone?, username, password, confirmPassword, termsAccepted, marketingConsent?, referralSource? }
  - VerifyRequest { credential, otp }
  - RefreshTokenRequest { refreshToken }
  - LoginResponse { accessToken, refreshToken, verificationRequired }
  - RegisterResponse { emailVerificationRequired, verificationLink, redirectUrl?, validationErrors? }
  - VerifyResponse { success }
- Account
  - EditProfileRequest { phone?, address? }
  - ProfileResponse { firstName, lastName, email, phone, address }
- Orders
  - OrderRequest { username, productName, robotCode, robotContainerCode, endpoint, approved? }
  - OrderResponse { orderId, orderCode, productName, robotCode, robotContainerCode, endpoint, price, status, createdAt, completedAt }
  - PickupOtpRequest { orderCode }, PickupVerifyOtpRequest { orderCode, otp[6 digits], tripCode }
  - PickupResponse { orderCode, status, otpSentTo, verified }
  - QRCodeResponse { orderCode, qrCodeBase64, robotCode, message }
- Robot web responses (wrapped paths)
  - LocationResponse { robotCode, lat, lon, roomCode }, BatteryResponse { robotCode, battery }, StatusResponse { robotCode, status }, ContainerStatusResponse { robotCode, containerCode, status }
  - Command responses: MoveCommandResponse, PickupCommandResponse, LoadCommandResponse, CommandResponse

## 4) API Endpoints

Security
- Authentication exempt: /api/auth/**
- All other endpoints require Authorization: Bearer <access-token>
- Role claim is embedded in JWT ("role"). The staff-only order endpoint validates role from token manually.

Auth (/api/auth)
- POST /login
  - Body: LoginRequest
  - Success: ApiResponse<LoginResponse> with accessToken and refreshToken
  - Errors: 401 invalid credentials; 403 if user status=PENDING (email verification required)
- POST /register
  - Body: RegisterRequest (validations: names, email format, password strength, termsAccepted=true)
  - Success: ApiResponse<RegisterResponse> with verification link and emailVerificationRequired=true
- POST /verify-otp
  - Body: VerifyRequest { credential (email or username), otp }
  - Success: ApiResponse<VerifyResponse { success:true }>
- GET /resend-otp?credential={emailOrUsername}
  - Success: ApiResponse<Object> message "OTP resent successfully ..."
- POST /logout
  - Header: Authorization: Bearer <accessToken>
  - Effect: Blacklists access token
  - Returns: ApiResponse<Void> success or error
- POST /refresh-token
  - Body: RefreshTokenRequest
  - Success: ApiResponse<LoginResponse> new access/refresh tokens

Account (/api/account)
- GET /profile
  - Returns: ApiResponse<ProfileResponse> for current authenticated user
- PUT /edit-profile
  - Body: EditProfileRequest { phone?, address? }
  - Returns: ApiResponse<ProfileResponse> updated data

Order (/api/order)
- POST /create
  - Body: OrderRequest
  - Validations via cache: robot online and status="free"; container status="free"
  - Creates Product, Trip (if needed), Order(PENDING)
  - Returns: ApiResponse<OrderResponse>
- GET /get?username={optional}
  - Returns: ApiResponse<List<OrderResponse>> (by username if provided; else all)
- GET /staff/all
  - Requires JWT with role=STAFF (validated by extracting "role" claim)
  - Returns: ApiResponse<List<OrderResponse>>
- GET /approve/{orderCode}
  - Sets Order.status=APPROVED; returns ApiResponse<Boolean>
- GET /generate-qr?orderCode=X
  - Generates Base64 QR; publishes to MQTT topic robot/{robotCode}/command/qr
  - Returns: ApiResponse<QRCodeResponse>
- POST /pickup/send-otp
  - Body: PickupOtpRequest { orderCode }
  - Precondition: Order.status must be DELIVERED
  - Generates OTP keyed by orderCode; emails OTP to user's email
  - Returns: ApiResponse<PickupResponse { status=OTP_SENT, otpSentTo masked }>
- POST /pickup/verify-otp
  - Body: PickupVerifyOtpRequest { orderCode, otp, tripCode }
  - On valid OTP: Order status set (COMPLETED/FINISHED), Trip COMPLETED via TripStatusService; returns ApiResponse<PickupResponse { verified:true }>

Trip (/api/trip)
- GET /by-order-id?orderId=<UUID>
- GET /by-order-code?orderCode=X
- GET /order/code/{orderCode}
  - All return: ApiResponse<TripResponse> (robotCode, tripCode, status, times)
- GET /progress/{tripCode} and GET /progress?tripCode=X
  - Returns: ApiResponse<TripProgressResponse { tripCode, status, progress }>, where progress is read from Redis cache

Robot Status (cache DTO responses, not ApiResponse envelope) (/api/robot/status/{robotId})
- GET /location → RobotLocationDTO
- GET /battery → RobotBatteryDTO
- GET / → RobotStatusDTO
- GET /container/{containerCode} → RobotContainerStatusDTO
  - Returns 404 via GlobalHandlingException if data missing

Robot “Message” read endpoints (wrapped) (/api/robot/message/{robotCode})
- GET /container/{containerCode}/status → ApiResponse<ContainerStatusResponse>
- GET /location → ApiResponse<LocationResponse>
- GET /battery → ApiResponse<BatteryResponse>
- GET /status → ApiResponse<StatusResponse>
- GET /connection → { status, timestamp, data: { online, lastSeen } }
- GET /info → { status, timestamp, robotCode, data: { location?, battery?, status?, connection? } }

Robot Commands (/api/robot/command)
- POST /request-status
  - Sends request-status command to all robots in DB; aggregates “free robots with free containers” from cache
  - Returns: ApiResponse<Map> with robotsRequested, freeRobots list
- POST /{robotCode}/move
  - Body: { lat, lon, roomCode }
  - Publishes to robot/{robotCode}/command/move; returns ApiResponse<MoveCommandResponse>
- POST /{robotCode}/trip/{tripCode}/move
  - Publishes to robot/{robotCode}/command/trip/{tripCode}/move
- POST /{robotCode}/container/{containerCode}/pickup
  - Body: { pickup: boolean }
  - Publishes to robot/{robotCode}/container/{containerCode}/command/pickup
- POST /{robotCode}/container/{containerCode}/trip/{tripCode}/pickup
  - Publishes to robot/{robotCode}/container/{containerCode}/command/trip/{tripCode}/pickup
- POST /{robotCode}/container/{containerCode}/load
  - Body: { load: boolean }
  - Publishes to robot/{robotCode}/container/{containerCode}/command/load
- POST /{robotCode}/container/{containerCode}/trip/{tripCode}/load
  - Publishes to robot/{robotCode}/container/{containerCode}/command/trip/{tripCode}/load
- POST /{robotCode}/trip/{tripCode}/continue
  - Validates trip by code+robot association; updates Trip.status to "continuing"; publishes to robot/{robotCode}/command/trip/{tripCode}/continue

## 5) Configuration & Deployment Notes

Key configuration (src/main/resources/application.yaml)
- Server
  - port: 8080, address: 0.0.0.0
- Datasource (MySQL)
  - url: jdbc:mysql://localhost:3306/zippy
  - username/password: set here; override via env (SPRING_DATASOURCE_URL, etc.)
  - Hikari pool configs
- JPA
  - ddl-auto: update, show-sql: true, dialect: MySQL8
- Mail (JavaMail)
  - host/port/username/password configured; use secure values via env
- Redis
  - spring.data.redis.host/port for RedisTemplate serializers (StringRedisSerializer)
  - EmbeddedRedisConfig uses spring.redis.host/port (primary connection factory)
- JWT (application.security.jwt)
  - secret-key: base64-encoded; access-token.expiration (ms), refresh-token.expiration (ms)
- MQTT (mqtt.*)
  - broker, client-id, username, password
  - inboundTopics:
    - robot/+/location
    - robot/+/battery
    - robot/+/status
    - robot/+/container/+/status
    - robot/+/trip/+
  - outboundTopics (reference patterns; publishing uses explicit topics in RobotCommandService)
  - qos: 1
- Dummy data
  - app.load-dummy-data: true enables DataLoader to run data/dummy_data.sql

External services required
- MySQL 8
- Redis
- MQTT broker (e.g., Mosquitto)
- SMTP for email

Security
- JWT-based auth; /api/auth/** is publicly accessible; all others require Bearer tokens.
- BCrypt password hashing.
- Role claim embedded in access token; resource-level role checks must be implemented per endpoint (example: staff endpoint checks "role" claim).

Deployment and run
- Provide environment variables or override application.yaml for credentials/secrets.
- Start the app (Maven):
  - mvn spring-boot:run
- Or build and run jar:
  - mvn clean package
  - java -jar target/zippy-backend-*.jar

## 6) Example Usage

User onboarding and order-to-delivery flow
1) Register
- POST /api/auth/register with RegisterRequest
- Receive ApiResponse with emailVerificationRequired=true

2) Verify OTP
- POST /api/auth/verify-otp with { credential: "<email or username>", otp: "123456" }
- Success returns ApiResponse { success: true }

3) Login
- POST /api/auth/login → get accessToken and refreshToken
- Use Authorization: Bearer <accessToken> for subsequent requests

4) Pick a robot/container (ensure available)
- Call POST /api/robot/command/request-status → see freeRobots list with freeContainers

5) Create order
- POST /api/order/create with { username, productName, robotCode, robotContainerCode, endpoint }
- Returns OrderResponse with orderCode and tripCode (via trip link)

6) Robot brings item; robot publishes progress to robot/{robotCode}/trip/{tripCode}; TripStatusService sets trip to DELIVERED and associated order(s) to DELIVERED; view via:
- GET /api/trip/progress/{tripCode}
- GET /api/order/get?username=<username>

7) Pickup OTP flow
- POST /api/order/pickup/send-otp { orderCode } → emails OTP to user, returns masked email
- POST /api/order/pickup/verify-otp { orderCode, otp, tripCode } → completes order and trip
- Verify with GET /api/order/get?username=... and GET /api/trip/by-order-code?orderCode=...

8) Optional: QR delivery to robot
- GET /api/order/generate-qr?orderCode=<code> → generates Base64 PNG and publishes to robot/{robotCode}/command/qr

Robot control example
- Move robot
  - POST /api/robot/command/{robotCode}/move { lat, lon, roomCode } → publishes to robot/{robotCode}/command/move
- Container load/pickup
  - POST /api/robot/command/{robotCode}/container/{containerCode}/load { load: true|false }
  - POST /api/robot/command/{robotCode}/container/{containerCode}/pickup { pickup: true|false }
- Trip-based variants and continue
  - POST /api/robot/command/{robotCode}/trip/{tripCode}/move ...
  - POST /api/robot/command/{robotCode}/trip/{tripCode}/continue

## 7) Diagram Recommendations (optional)

- System architecture
  - Show client → REST API → Controllers → Services → (Repositories/MySQL, Redis) and MQTT paths:
    - Outbound commands via Spring Integration mqttOutboundChannel → broker → robots.
    - Inbound telemetry via Paho subscriber → RobotMessageService → RobotDataService/TripStatusService.
- Database ERD
  - Entities: User, Role, Robot, RobotContainer, Trip, Product, Order, Payment with FKs and cardinalities.
- Request/response sequences
  - Auth register/verify/login flow.
  - Order create → trip create/update → robot MQTT progress → TripStatusService status updates → pickup OTP verification.
  - Robot command publish and robot telemetry ingestion.

Notes and considerations
- Consistency of API envelopes: RobotStatusController returns raw DTOs, while RobotMessageController uses ApiResponse; standardizing responses may improve client integration.
- Redis config duplication: both spring.data.redis.* and spring.redis.* are present; EmbeddedRedisConfig defines a @Primary connection factory on spring.redis.* values and RedisConfig configures RedisTemplate on spring.data.redis.*. Keep them consistent to avoid confusion.
- Email @Async methods require @EnableAsync to truly run asynchronously.

