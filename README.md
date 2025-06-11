# Zippy Backend Service

## Overview
Zippy Backend is a Spring Boot application that provides authentication, user management, and business logic for the Zippy platform.

## Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MySQL 8.0.42 (or your configured database)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/zippy-backend.git
   cd zippy-backend
   ```

2. **Configure the database**
   
   Edit `src/main/resources/application.yaml` to match your database configuration.

3. **Build the project**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   The application will start on port 8080 by default.

## API Usage

### Authentication Endpoints

#### User Registration
Register a new user account with email verification.

```http
POST /api/auth/register
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+1234567890",
  "password": "SecurePass1!",
  "confirmPassword": "SecurePass1!",
  "termsAccepted": true
}
```

**Response**:
```json
{
  "success": true,
  "message": "Registration successful. Please verify your email address.",
  "userId": 1,
  "emailVerificationRequired": true,
  "verificationLink": "/api/auth/verify-otp?email=john@example.com",
  "timestamp": "2025-06-05 12:34:56"
}
```

#### Email Verification (OTP)
Verify the user's email with the OTP sent during registration.

```http
POST /api/auth/verify-otp?email=john@example.com&otp=123456
```

**Response**:
```json
{
  "success": true,
  "message": "OTP verification successful",
  "data": null
}
```

#### Resend OTP
Request a new OTP if the original expires or is lost.

```http
POST /api/auth/resend-otp?email=john@example.com
```

**Response**:
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "data": null
}
```

#### User Login
Authenticate and receive JWT tokens.

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "john@example.com",
  "password": "SecurePass1!"
}
```

**Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2025-06-05 14:34:56",
  "expiresIn": 3600
}
```

### Using Authentication Tokens

After receiving the tokens from the login endpoint:

1. **Include the access token in API requests**
   ```http
   GET /api/some-protected-resource
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ```

2. **Decode the token on the frontend**
   
   The access token contains user information that can be decoded on the client side:
   ```javascript
   // Example using jwt-decode library in JavaScript
   import jwt_decode from "jwt-decode";
   
   const token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
   const decoded = jwt_decode(token);
   
   console.log(decoded.sub); // User's email
   console.log(decoded.roles); // User's roles
   ```

3. **Refresh the token when needed**
   
   When the access token expires, use the refresh token to get a new one (endpoint to be implemented).

## Error Handling

The API uses standardized error responses:

```json
{
  "success": false,
  "message": "Error message",
  "data": {
    "timestamp": "2025-06-05 12:34:56",
    "path": "/api/endpoint",
    "error": "Error type"
  }
}
```

Common HTTP status codes:
- 400: Bad Request (validation errors)
- 401: Unauthorized (authentication required)
- 403: Forbidden (insufficient permissions)
- 404: Not Found (resource doesn't exist)
- 500: Internal Server Error (server-side issue)

## Development

### Project Structure

- `config/`: Configuration classes for Spring Security, JWT, etc.
- `controller/`: REST API endpoints
- `exception/`: Error handling
- `model/`: Data models and DTOs
- `repository/`: Database access interfaces
- `service/`: Business logic

### Adding New Endpoints

1. Create DTOs in `model/dto/request` and `model/dto/response`
2. Add endpoint methods in appropriate controller classes
3. Implement business logic in service classes
4. Add appropriate unit tests

## Security Notes

- JWT tokens expire after 1 hour by default
- Passwords are hashed with BCrypt
- Email verification is required upon registration
- Protected endpoints require authentication
# zippy-backend
# zippy-backend
# zippy-backend
# zippy-backend
