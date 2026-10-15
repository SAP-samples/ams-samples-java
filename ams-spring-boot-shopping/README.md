# AMS Spring Boot Shopping Sample

## Overview

This is a sample for the plain Spring Boot (i.e., non-CAP) integration of the AMS Java library. It shows a simplified shopping REST API, secured with Spring Security. The sample integrates both SAP Authorization Management Service (AMS) and the SAP Identity Authentication Service (IAS). Route-level authorization pre-checks are implemented via [`AmsRouteSecurity`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java), while fine-grained instance-based checks are demonstrated both via custom AMS Spring annotations and manually in service logic.

## Domain Entities

| Entity | Description | Source |
|--------|-------------|--------|
| **Product** | `id`, `name`, `price`, `category` | [`Product.java`](src/main/java/com/sap/cloud/security/ams/samples/model/Product.java), seeded from [`src/main/resources/csv/products.csv`](src/main/resources/csv/products.csv) |
| **Order** | `id`, `productId`, `quantity`, `totalAmount`, `createdBy` (SCIM id) | [`Order.java`](src/main/java/com/sap/cloud/security/ams/samples/model/Order.java), seeded from [`src/main/resources/csv/orders.csv`](src/main/resources/csv/orders.csv) |
| **HealthStatus** | Health response payload | [`HealthStatus.java`](src/main/java/com/sap/cloud/security/ams/samples/model/HealthStatus.java) |

Persistence is in-memory via [`SimpleDatabase`](src/main/java/com/sap/cloud/security/ams/samples/db/SimpleDatabase.java) and [`DataLoader`](src/main/java/com/sap/cloud/security/ams/samples/db/DataLoader.java).

## Services

| Component | Responsibility                                                                                             |
|-----------|------------------------------------------------------------------------------------------------------------|
| [`ProductsService`](src/main/java/com/sap/cloud/security/ams/samples/service/ProductsService.java) | `GET /products`                                                                                            |
| [`OrdersService`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) | `GET` / `POST` / `DELETE` on `/orders`; instance-based AMS checks for list, create, and delete             |
| [`PrivilegesService`](src/main/java/com/sap/cloud/security/ams/samples/service/PrivilegesService.java) | `GET /privileges` — returns privileges for the current user (without the instance-based filter conditions) |
| [`ProductsController`](src/main/java/com/sap/cloud/security/ams/samples/controller/ProductsController.java), [`OrdersController`](src/main/java/com/sap/cloud/security/ams/samples/controller/OrdersController.java), [`PrivilegesController`](src/main/java/com/sap/cloud/security/ams/samples/controller/PrivilegesController.java), [`HealthController`](src/main/java/com/sap/cloud/security/ams/samples/controller/HealthController.java) | REST endpoints                                                                                             |

Application entry point: [`Application`](src/main/java/com/sap/cloud/security/ams/samples/Application.java). Privilege constants: [`Privileges`](src/main/java/com/sap/cloud/security/ams/samples/config/Privileges.java). Context attributes: [`AmsAttributes`](src/main/java/com/sap/cloud/security/ams/samples/config/AmsAttributes.java).

## API

| Method | Path | Auth |
|--------|------|------|
| `GET` | `/actuator/health` | Public |
| `GET` | `/health` | Public |
| `GET` | `/privileges` | Authenticated |
| `GET` | `/products` | `read:products` |
| `GET` | `/orders` | `read:orders` (with optional row filtering) |
| `POST` | `/orders` | `create:orders` (with per-order attribute checks) |
| `DELETE` | `/orders/{id}` | `delete:orders` |

Request body for `POST /orders`: JSON `{ "productId": number, "quantity": number }`.

## Authorization model

Privileges are defined in [`Privileges`](src/main/java/com/sap/cloud/security/ams/samples/config/Privileges.java) and granted by AMS policies. Enforcement is split between the route layer ([`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) and `AmsRouteSecurity`) and the service or controller layer where instance-based authorization is implemented.

| Operation | AMS privilege | Typical policy | Route-level check | Contextual / service check |
|-----------|-----------------|----------------|-------------------|----------------------------|
| `GET /actuator/health` | — | — | `permitAll` in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) | — |
| `GET /health` | — | — | Public (no AMS check on route) | [`HealthController`](src/main/java/com/sap/cloud/security/ams/samples/controller/HealthController.java) |
| `GET /privileges` | — | — | `authenticated()` in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) | [`PrivilegesService`](src/main/java/com/sap/cloud/security/ams/samples/service/PrivilegesService.java) |
| `GET /products` | `read:products` | `ReadProducts` | `via.checkPrivilege(READ_PRODUCTS)` in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) | [`ProductsService.getProducts`](src/main/java/com/sap/cloud/security/ams/samples/service/ProductsService.java) `@CheckPrivilege` |
| `GET /orders` | `read:orders` | `ReadOrders`, `ReadOwnOrders` | `via.precheckPrivilege(READ_ORDERS)` in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) | [`OrdersService.getOrders`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) — deny, full list, or instance-based filter |
| `POST /orders` | `create:orders` | `CreateOrders` | `via.precheckPrivilege(CREATE_ORDERS)` in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) | [`OrdersController.createOrder`](src/main/java/com/sap/cloud/security/ams/samples/controller/OrdersController.java) `@PrecheckPrivilege`; instance-based check in [`OrdersService.createOrder`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) |
| `DELETE /orders/{id}` | `delete:orders` | `DeleteOrders` | `via.checkPrivilege(DELETE_ORDERS)` in [`SecurityConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/SecurityConfiguration.java) | [`OrdersService.deleteOrder`](src/main/java/com/sap/cloud/security/ams/samples/service/OrdersService.java) `@CheckPrivilege` |

**App2App communication**: External principal propagation and technical user requests from other applications may act only with controlled privileges as defined by the policy mappers in [`App2AppAuthorizationConfiguration`](src/main/java/com/sap/cloud/security/ams/samples/config/App2AppAuthorizationConfiguration.java); internal policies are listed under Policy definitions (DCL) below.

## Policy definitions (DCL)

| Package | File | Purpose |
|---------|------|---------|
| `shopping` | [`src/main/resources/ams/dcl/shopping/basePolicies.dcl`](src/main/resources/ams/dcl/shopping/basePolicies.dcl) | `ReadProducts`, `ReadOrders`, `ReadOwnOrders`, `CreateOrders`, `DeleteOrders` |
| `local` | [`src/main/resources/ams/dcl/local/adminPolicies.dcl`](src/main/resources/ams/dcl/local/adminPolicies.dcl) | Mocked derived policies for testing, e.g. `OrderAccessory` (restricted create) |
| `internal` | [`src/main/resources/ams/dcl/internal/internalPolicies.dcl`](src/main/resources/ams/dcl/internal/internalPolicies.dcl) | Internal App2App policies (inaccessible by administrators) |
| Schema | [`src/main/resources/ams/dcl/schema.dcl`](src/main/resources/ams/dcl/schema.dcl) | Attributes for instance-based authorization `order.*`, `product.category`, `$user.scim_id` |

## Running tests

From this directory:

```bash
mvn test
```

| Test class | Focus |
|------------|--------|
| [`ApplicationTest`](src/test/java/com/sap/cloud/security/ams/samples/ApplicationTest.java) | HTTP integration tests with `@ActiveProfiles("test")`, [`TestSecurityConfiguration`](src/test/java/com/sap/cloud/security/ams/samples/config/TestSecurityConfiguration.java), and JWT fixtures under `src/test/resources/jwt/` |

Tests use a local AMS DCN compiled to `target/generated-test-resources/ams/dcn` and policy assignments in [`src/test/resources/mockPolicyAssignments.json`](src/test/resources/mockPolicyAssignments.json). Run tests with Maven so the DCN is generated before execution.

## License

Licensed under the Apache License 2.0 — see the [LICENSE](../LICENSE) file in the repository root.
