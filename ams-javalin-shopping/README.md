# AMS Javalin Shopping Sample

A Java shopping application sample using Javalin framework and SAP Authorization Management Service (AMS). This project demonstrates how to build a RESTful API with authentication and authorization using Javalin as an alternative to Spring Boot.

## Overview

This sample is a Java equivalent of the Node.js Express shopping sample, showcasing:

- **Javalin Framework**: Lightweight Java web framework for REST APIs
- **SAP AMS Integration**: Authorization Management Service for role-based access control
- **In-memory Database**: Simple CSV-based data initialization
- **Authentication & Authorization**: Configurable auth handlers for production and testing
- **Clean Architecture**: Modular design with separation of concerns

## Project Structure

```
ams-javalin-shopping/
├── src/main/java/com/sap/ams/cloud/security/samples/
│   ├── JavalinShoppingApplication.java    # Main application entry point
│   ├── AppFactory.java                     # Configurable app builder
│   ├── auth/
│   │   └── AuthHandler.java               # Production auth handler (AMS integration)
│   ├── service/
│   │   ├── ProductsService.java           # Products REST service
│   │   ├── OrdersService.java             # Orders REST service
│   │   └── PrivilegesService.java         # User privileges service
│   ├── db/
│   │   ├── SimpleDatabase.java            # In-memory database
│   │   └── DataLoader.java                # CSV data loader
│   └── model/
│       ├── Product.java                   # Product entity
│       ├── Order.java                     # Order entity
│       └── HealthStatus.java              # Health check model
├── src/main/resources/
│   └── csv/
│       ├── products.csv                   # Initial product data
│       └── orders.csv                     # Initial order data
└── src/test/java/com/sap/ams/cloud/security/samples/
    ├── JavalinShoppingApplicationTest.java # Integration tests
    └── auth/
        └── MockAuthHandler.java           # Mock auth for testing
```

## Features

### API Endpoints

- `GET /health` - Health check endpoint (accessible to ANYONE)
- `GET /products` - Get all products (requires authentication)
- `GET /orders` - Get user orders with contextual filtering
- `POST /orders` - Create new order with business validation
- `DELETE /orders/{id}` - Delete specific order
- `GET /privileges` - Get user's potential privileges for UI

### Key Components

1. **Configurable Authentication**: Factory pattern allows different auth handlers for production vs testing
2. **Role-based Authorization**: Health endpoint accessible to "ANYONE", others require proper authentication
3. **CSV-based Initialization**: Data loaded from CSV files similar to the Node.js version
4. **Error Handling**: Comprehensive error handling with proper HTTP status codes
5. **Logging**: Structured logging throughout the application

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Building the Application

```bash
cd ams-javalin-shopping
mvn clean compile
```

### Running the Application

```bash
mvn exec:java
```

Or with custom port:

```bash
mvn exec:java -Dserver.port=8080
```

The application will start on port 7000 by default and be available at:
- Health check: http://localhost:7000/health
- API endpoints: http://localhost:7000/products, etc.

### Running Tests

```bash
mvn test
```

## Configuration

### Environment Variables

- `PORT` - Server port (default: 7000)

### System Properties

- `server.port` - Alternative way to set server port

## Authentication & Authorization

### Production Mode

The `AuthHandler` class provides production authentication using SAP AMS. Currently implemented as stubs that can be extended with actual AMS integration:

```java
// TODO: Implement actual AMS authentication and authorization
// This would include:
// 1. Extract JWT token from Authorization header
// 2. Validate token with Identity Service
// 3. Create security context
// 4. Perform authorization checks with AMS
```

### Testing Mode

The `MockAuthHandler` allows testing with simple Basic Authentication:

```bash
curl -H "Authorization: Basic $(echo -n 'testuser:password' | base64)" \
     http://localhost:7000/products
```

## Sample Data

### Products
- MacBook Pro M3 (pc category)
- Dell Monitor (monitor category)  
- Apple Mouse (accessory category)
- Cherry Keyboard (accessory category)
- Yubikey (securityAccessory category)

### Orders
- Pre-loaded orders for users: carol, alice, bob

## Dependencies

### Main Dependencies
- **Javalin 5.6.3** - Web framework
- **Jackson** - JSON processing
- **OpenCSV** - CSV file processing
- **SAP Jakarta AMS 3.9.0-SNAPSHOT** - Authorization Management Service
- **SLF4J** - Logging

### Test Dependencies
- **JUnit 5** - Testing framework
- **Javalin Test Tools** - Integration testing support

## Future Enhancements

1. **Complete AMS Integration**: Implement actual authentication and authorization with SAP AMS
2. **Database Integration**: Replace in-memory database with persistent storage
3. **Advanced Authorization**: Implement contextual authorization checks
4. **API Documentation**: Add OpenAPI/Swagger documentation
5. **Monitoring**: Add metrics and health indicators
6. **Security**: Implement HTTPS, CORS configuration, and security headers

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
