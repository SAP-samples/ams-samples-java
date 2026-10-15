# AMS CAP Bookshop Sample

## Overview

This is a sample for the CAP Spring Boot integration of the AMS Java library. It is a typical SAP Cloud Application Programming Model (CAP) bookshop that combines CDS declarative authorization (`@requires`, `@restrict`) with AMS policies to show standard role-based access checks. Additionally, it demonstrates how `@ams.attributes` annotations and conditional role assignments can be combined to restrict access based on attribute filters for instance-based authorization.

## Domain Entities

Defined in [`db/schema.cds`](db/schema.cds) (namespace `sap.capire.bookshop`):

| Entity | Description                                                                                                        | Source |
|--------|--------------------------------------------------------------------------------------------------------------------|--------|
| **Books** | `title`, `descr`, `author`, `stock`, `price`, `currency`, `genre`; `@ams.attributes` maps **Genre** to `genre.name` | [`db/schema.cds`](db/schema.cds), seed data in [`db/data/`](db/data/) |
| **Authors** | `name`, dates and places of birth/death; association to books                                                      | [`db/schema.cds`](db/schema.cds), seed data in [`db/data/`](db/data/) |
| **Genres** | Hierarchical code list                                                                                             | [`db/schema.cds`](db/schema.cds), seed data in [`db/data/`](db/data/) |

Persistence uses H2 at runtime (see [`srv/src/main/resources/application.yaml`](srv/src/main/resources/application.yaml)).

## Services

| Component | Responsibility |
|-----------|----------------|
| **CatalogService** | [`srv/cat-service.cds`](srv/cat-service.cds) — read-only book views, `submitOrder` action, `OrderedBook` event |
| **AdminService** | [`srv/admin-service.cds`](srv/admin-service.cds) — CRUD projections on Books and Authors with `@restrict` |
| [`CatalogServiceHandler`](srv/src/main/java/customer/ams_cap_bookshop/handlers/CatalogServiceHandler.java) | Stock update for `submitOrder`, discount display logic on read |

Application entry point: [`Application`](srv/src/main/java/customer/ams_cap_bookshop/Application.java).

## API

OData v4 endpoints (also used by Fiori apps under [`app/`](app/)):

| Method | Path | Auth |
|--------|------|------|
| OData | `/odata/v4/CatalogService/` | Catalog reads and `submitOrder` (not gated by AdminService roles) |
| OData | `/odata/v4/AdminService/` | Requires CAP roles `ManageAuthors` or `ManageBooks`; entity operations per `@restrict` |

Notable Catalog operations: read `Books`, `ListOfBooks`; action `submitOrder(book, quantity)`.

## Authorization model

While anyone can use the catalog service to browse through the shop, there are two roles (`ManageBooks`, `ManageAuthors`) defined in the CAP model (see [`srv/admin-service.cds`](srv/admin-service.cds)) for management purposes. The policy intended for the business role `StockManager` grants only the former (optionally restricted to a specific book genre), while the policy for the business role `ContentManager` grants both roles.

| CAP role | AMS policy | Typical effect | Enforcement |
|----------|------------|----------------|-------------|
| `ManageBooks` | `cap.StockManager`, `cap.ContentManager` | Manage book stock/content; genre may be restricted by policy | [`srv/admin-service.cds`](srv/admin-service.cds) `@restrict` on `Books` |
| `ManageAuthors` | `cap.ContentManager` | Manage authors | [`srv/admin-service.cds`](srv/admin-service.cds) `@restrict` on `Authors` |
| `ManageBooks` (restricted) | `local.StockManagerFiction` | `ManageBooks` limited to Mystery and Fantasy genres | [`srv/src/main/resources/ams/local/testPolicies.dcl`](srv/src/main/resources/ams/local/testPolicies.dcl) |

## Policy definitions (DCL)

| Package | File | Purpose                                                                                          |
|---------|------|--------------------------------------------------------------------------------------------------|
| `cap` | [`srv/src/main/resources/ams/cap/basePolicies.dcl`](srv/src/main/resources/ams/cap/basePolicies.dcl) | `StockManager`, `ContentManager` — assign management roles                                       |
| `local` | [`srv/src/main/resources/ams/local/testPolicies.dcl`](srv/src/main/resources/ams/local/testPolicies.dcl) | Mocked derived policies for testing, e.g. `StockManagerFiction` (genre-restricted `ManageBooks`) |
| Schema | [`srv/src/main/resources/ams/schema.dcl`](srv/src/main/resources/ams/schema.dcl) | Attributes for instance-based authorization (`Genre`)                                            |

## Running tests

From this directory:

```bash
mvn test
```

This builds and tests the [`srv`](srv/) module.

| Test class | Focus |
|------------|--------|
| [`AdminServiceHandlerTest`](srv/src/test/java/customer/ams_cap_bookshop/handlers/AdminServiceHandlerTest.java) | AdminService CRUD with `@WithMockUser` (`stock-manager`, `content-manager`, `stock-manager-fiction`) |
| [`CatalogServiceHandlerTest`](srv/src/test/java/customer/ams_cap_bookshop/handlers/CatalogServiceHandlerTest.java) | Unit tests for discount logic in `CatalogServiceHandler` |

## License

Licensed under the Apache License 2.0 — see the [LICENSE](../LICENSE) file in the repository root.
