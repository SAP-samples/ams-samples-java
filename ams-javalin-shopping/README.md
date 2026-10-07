# AMS Javalin Shopping Sample

## Overview

This is a sample for the plain Java (i.e. non-Spring) API of the AMS Java library. It is a simplified shopping REST API built with the lightweight [Javalin](https://javalin.io/) framework which was chosen due to its simplicity and brevity in order to put the focus on the AMS specific parts of the implementation. The sample integrates both SAP Authorization Management Service (AMS) and the SAP Identity Authentication Service (IAS). Route-level authorization pre-checks are implemented in [`AuthHandler`](src/main/java/com/sap/cloud/security/ams/samples/auth/AuthHandler.java) while fine-grained contextual checks are implemented in the service handlers.

## Domain Entities

| Entity | Description | Source |
|--------|-------------|--------|
| **Product** | `id`, `name`, `price`, `category` | [`Product.java`](src/main/java/com/sap/cloud/security/ams/samples/model/Product.java), seeded from [`src/main/resources/csv/products.csv`](src/main/resources/csv/products.csv) |
| **Order** | `id`, `productId`, `quantity`, `totalAmount`, `createdBy` (SCIM id) | [`Order.java`](src/main/java/com/sap/cloud/security/ams/samples/model/Order.java), seeded from [`src/main/resources/csv/orders.csv`](src/main/resources/csv/orders.csv) |
| **HealthStatus** | Health response payload | [`HealthStatus.java`](src/main/java/com/sap/cloud/security/ams/samples/model/HealthStatus.java) |

Persistence is in-memory via [`SimpleDatabase`](src/main/java/com/sap/cloud/security/ams/samples/db/SimpleDatabase.java) and [`DataLoader`](src/main/java/com/sap/cloud/security/ams/samples/db/DataLoader.java).

## Services

| Component | Responsibility                                                                                                                                             |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`ProductsService`](src/main/java/com/sap/cloud/security/ams/samples/service/ProductsService.java) | `GET /products`                                                                                                                                            |
| [`OrdersService`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) | `GET` / `POST` / `DELETE` on `/orders`; contextual AMS checks for list, create, and optional in-handler delete check                                       |
| [`PrivilegesService`](src/main/java/com/sap/cloud/security/ams/samples/service/PrivilegesService.java) | `GET /privileges` — returns potential privileges for the current user                                                                                      |
| [`AuthHandler`](src/main/java/com/sap/cloud/security/ams/samples/auth/AuthHandler.java) | IAS JWT authentication and route-level authorization via [`ShoppingAuthorizations`](src/main/java/com/sap/cloud/security/ams/samples/auth/ShoppingAuthorizations.java) |
| [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java) | Wires together the different application components on start-up                                                                                            |

Production entry point: [`JavalinShoppingApplication`](src/main/java/com/sap/cloud/security/ams/samples/JavalinShoppingApplication.java).

## API

| Method | Path | Auth                                              |
|--------|------|---------------------------------------------------|
| `GET` | `/health` | Public (503 until AMS library is ready, then 200) |
| `GET` | `/privileges` | Authenticated                                     |
| `GET` | `/products` | `read:products`                                   |
| `GET` | `/orders` | `read:orders` (with optional row filtering)       |
| `POST` | `/orders` | `create:orders` (with per-order attribute checks) |
| `DELETE` | `/orders/{id}` | `delete:orders`                                   |

Request body for `POST /orders`: JSON `{ "productId": number, "quantity": number }`.

## Authorization model

Privileges are defined in [`Role`](src/main/java/com/sap/cloud/security/ams/samples/auth/Role.java) and granted by AMS policies. Enforcement is split between the route layer (`AuthHandler` + Javalin route roles in [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java)) and the service layer where instance-based authorization is implemented.

| Operation | AMS privilege | Typical policy | Route-level check | Contextual / service check |
|-----------|-----------------|----------------|-------------------|----------------------------|
| `GET /health` | — | — | No AMS role on route | [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java) waits for AMS before reporting UP |
| `GET /privileges` | — | — | [`Role.AUTHENTICATED`](src/main/java/com/sap/cloud/security/ams/samples/auth/Role.java) in [`AuthHandler.authorize`](src/main/java/com/sap/cloud/security/ams/samples/auth/AuthHandler.java) | [`PrivilegesService`](src/main/java/com/sap/cloud/security/ams/samples/service/PrivilegesService.java) |
| `GET /products` | `read:products` | `ReadProducts` | `Role.READ_PRODUCTS` in [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java) | Route only |
| `GET /orders` | `read:orders` | `ReadOrders`, `ReadOwnOrders` | `Role.READ_ORDERS` in [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java) | [`OrdersService.getOrders`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) — deny, full list, or filter via [`ShoppingAuthorizations.checkRole`](src/main/java/com/sap/cloud/security/ams/samples/auth/ShoppingAuthorizations.java) |
| `POST /orders` | `create:orders` | `CreateOrders` | `Role.CREATE_ORDERS` in [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java) | [`OrdersService.createOrder`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) — [`checkCreateOrder`](src/main/java/com/sap/cloud/security/ams/samples/auth/ShoppingAuthorizations.java) with product category and order total |
| `DELETE /orders/{id}` | `delete:orders` | `DeleteOrders` | `Role.DELETE_ORDERS` in [`AppFactory`](src/main/java/com/sap/cloud/security/ams/samples/AppFactory.java) | Route only (see commented equivalent check in [`OrdersService.deleteOrder`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java)) |

**App2App communication**: External principal propagation and technical user requests from other applications may act only with controlled privileges as defined by the policy mappers in [`AuthHandler.createAuthProvider`](src/main/java/com/sap/cloud/security/ams/samples/auth/AuthHandler.java), see [`PolicyTest`](src/test/java/com/sap/cloud/security/ams/samples/auth/PolicyTest.java).

## Policy definitions (DCL)

| Package | File | Purpose                                                                                    |
|---------|------|--------------------------------------------------------------------------------------------|
| `shopping` | [`src/main/resources/ams/dcl/shopping/basePolicies.dcl`](src/main/resources/ams/dcl/shopping/basePolicies.dcl) | `ReadProducts`, `ReadOrders`, `ReadOwnOrders`, `CreateOrders`, `DeleteOrders`              |
| `local` | [`src/main/resources/ams/dcl/local/adminPolicies.dcl`](src/main/resources/ams/dcl/local/adminPolicies.dcl) | Mocked derived policies for testing, e.g. `OrderAccessory` (restricted create)             |
| `internal` | [`src/main/resources/ams/dcl/internal/internalPolicies.dcl`](src/main/resources/ams/dcl/internal/internalPolicies.dcl) | Internal App2App policies (inaccessible by administrators)                                 |
| Schema | [`src/main/resources/ams/dcl/schema.dcl`](src/main/resources/ams/dcl/schema.dcl) | Attributes for instance-based authorization `order.*`, `product.category`, `$user.scim_id` |

## Running tests

From this directory:

```bash
mvn test
```

| Test class | Focus |
|------------|--------|
| [`JavalinShoppingApplicationTest`](src/test/java/com/sap/cloud/security/ams/samples/JavalinShoppingApplicationTest.java) | HTTP integration tests with [`TestAuthHandler`](src/test/java/com/sap/cloud/security/ams/samples/auth/TestAuthHandler.java) and JWT fixtures under `src/test/resources/jwt/` |
| [`PolicyTest`](src/test/java/com/sap/cloud/security/ams/samples/auth/PolicyTest.java) | DCL policy evaluation via local DCN (`AmsTestExtension`) |

Tests use a local AMS DCN compiled to `target/generated-test-resources/ams/dcn` and policy assignments in [`src/test/resources/mockPolicyAssignments.json`](src/test/resources/mockPolicyAssignments.json). Run tests with Maven so the DCN is generated before execution.

## License

Licensed under the Apache License 2.0 — see the [LICENSE](../LICENSE) file in the repository root.
