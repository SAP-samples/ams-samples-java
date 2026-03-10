[![REUSE status](https://api.reuse.software/badge/github.com/SAP-samples/ams-samples-java)](https://api.reuse.software/info/github.com/SAP-samples/ams-samples-java)

# Overview: Authorization Management Service (AMS) Java Samples

This repository brings together several sample applications that demonstrate how to integrate the **SAP Authorization Management Service (AMS)** into various Java frameworks. Each sample showcases different architectures and scenarios for authentication and authorization using AMS and the SAP Identity Authentication Service (IAS).

## ⚠️ Important Version Notice

**These samples use major version 4.** The older version 3 samples have been moved to the `legacy-v3-samples` directory. We **strongly recommend**:
- ✅ Use v4 for all new projects
- ✅ Upgrade existing v3 projects to v4
- ❌ Do not start new projects with v3

---

## Repository Structure and Sample Overview

| Sample Name                          | Description                                                                                                                         | Directory                  |
|-------------------------------------- |-------------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| **Javalin Shopping Sample**           | A lightweight application using **Javalin** (minimal web framework) to showcase the AMS v4 Core API        | `ams-javalin-shopping`     |
| **Spring Boot Shopping Sample**       | A Spring Boot application to showcase AMS **Spring Security** integration                               | `ams-spring-boot-shopping` |
| **CAP Spring Boot Bookshop Sample**   | Demonstrates the integration of AMS into a **SAP CAP Java** Spring Boot application    | `ams-cap-bookshop`         |

---

## Sample Details

### 1. Javalin Shopping Sample (`ams-javalin-shopping`)

- **Framework:** Javalin (lightweight Java web framework)
- **Highlights:**
  - Minimal web framework approach with AMS Core API v4
  - AMS and IAS integration for securing REST APIs via JWT tokens
  - Clean, straightforward implementation ideal for learning AMS basics
  - Simple shopping application example
- **More info:** [Javalin Shopping Sample](ams-javalin-shopping/README.md)

### 2. Spring Boot Shopping Sample (`ams-spring-boot-shopping`)

- **Framework:** Spring Boot
- **Highlights:**
  - AMS Core API v4 integration with Spring Boot
  - AMS and IAS integration for securing REST APIs via JWT tokens
  - Shopping application with Spring Security integration
- **More info:** [Spring Boot Shopping Sample](ams-spring-boot-shopping/README.md)

### 3. CAP Spring Boot Bookshop Sample (`ams-cap-bookshop`)

- **Framework:** SAP Cloud Application Programming Model (CAP) for Java
- **Highlights:**
  - Demonstrates AMS Core API v4 integration in a CAP Java project (bookshop sample)
  - Contains instructions for both local development and cloud deployment
- **More info:** [CAP Bookshop Sample](ams-cap-bookshop/README.md)

---

## Legacy Samples (v3)

The `legacy-v3-samples` directory contains samples for **AMS Core API v3**. These are maintained for reference but are **not recommended for new projects**:

- `spring-security-ams` - Spring Boot sample with v3 API
- `jakarta-ams-sample` - Jakarta EE sample with v3 API
- `ams-cap-sample` - CAP Java sample with v3 API
- `ams-ztis-sample` - Jakarta EE Zero Trust sample with v3 API

**Please migrate to v4 for new development and consider upgrading existing v3 projects.**

---

## What Can You Learn From These Samples?

- **AMS API integration** across different Java frameworks
- **Policy and access management** using AMS and DCL files
- **JWT-based authentication** with IAS (Identity Authentication Service)

---

## Further References

- [AMS Documentation](https://sap.github.io/cloud-identity-developer-guide/)
- [SAP Cloud Security Services Library](https://github.com/SAP/cloud-security-services-integration-library)
- [SAP CAP Documentation](https://cap.cloud.sap/docs/)
- [Javalin Documentation](https://javalin.io/)

---

## Licenses

This repository is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSES/Apache-2.0.txt) file for details.