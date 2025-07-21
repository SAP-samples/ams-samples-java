# Overview: Authorization Management Service (AMS) Java Samples

This repository brings together several sample applications that demonstrate how to integrate the **SAP Authorization
Management Service (AMS)** into various Java frameworks. Each sample showcases different architectures and scenarios for
authentication and authorization using AMS and the SAP Identity Authentication Service (IAS).

## Repository Structure and Sample Overview

| Sample Name            | Description                                                                                                                                                    | Typical Use Case                 | Directory             |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|-----------------------|
| **Spring Boot Sample** | A Spring Boot application using AMS and IAS for authentication & authorization with JWT. Includes deployment examples for Kubernetes (Kyma) and Cloud Foundry. | Microservices, Cloud-native apps | `spring-security-ams` |
| **Jakarta EE Sample**  | Example Jakarta EE application that integrates AMS for resource access authorization and uses IAS for authentication.                                          | Java EE/enterprise projects      | `jakarta-ams-sample`  |
| **CAP Java Sample**    | Demonstrates the integration of AMS into a **SAP CAP Java** application, including policy generation for CAP roles.                                            | SAP CAP projects                 | `ams_cap_sample`      |

---

## Sample Details

### 1. Spring Boot Sample (`spring-security-ams`)

- **Framework:** Spring Boot
- **Highlights:**
    - AMS and IAS integration for securing REST APIs via JWT tokens.
    - Example of using the SAP Application Router as a reverse proxy.
    - Provides DCL files to define authorization models.
    - Deployment instructions for both Kyma/Kubernetes and Cloud Foundry.
- **Typical Use:** Microservice-based Java applications needing SAP IAM integration.
- **More info:** [Spring Security AMS](spring-security-ams/README.md)

### 2. Jakarta EE Sample (`jakarta-ams-sample`)

- **Framework:** Jakarta EE
- **Highlights:**
    - Shows how to secure Java EE applications using AMS and IAS.
    - Uses the SAP Application Router as an OAuth 2.0 client.
    - Contains deployment guides for Kubernetes (Helm chart) and Cloud Foundry.
- **Typical Use:** Java EE or enterprise applications on SAP BTP.
- **More info:** [Jakarta AMS Sample](jakarta-ams-sample/README.md)

### 3. CAP Java Sample (`st_ams_cap_sample`)

- **Framework:** SAP Cloud Application Programming Model (CAP) for Java
- **Highlights:**
    - Demonstrates AMS integration in a CAP Java project (example: bookshop).
    - Shows policy generation for CAP security roles.
    - Contains instructions for both local development and Cloud Foundry deployment.
- **Typical Use:** Extending CAP Java projects with AMS-based authorization.
- **More info:** [AMS CAP Sample](ams-cap-sample/README.md)

---

## What Can You Learn From These Samples?

- **Central policy and access management** using AMS and DCL files.
- **Integrating SAP IAM** in different Java frameworks (Spring Boot, Jakarta EE, CAP).
- **Cloud-native deployment** patterns for SAP BTP (Kyma/Kubernetes & Cloud Foundry).
- **Mapping CAP roles to AMS policies** for unified authorization logic.

---

## Further References

- [AMS Documentation (internal)](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/)
- [SAP Cloud Security Services (public)](https://github.com/SAP/cloud-security-services-integration-library)
- [SAP CAP Documentation](https://cap.cloud.sap/docs/)

---

## Licenses
This repository is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSES/Apache-2.0.txt) file for details.

[![REUSE status](https://api.reuse.software/badge/github.com/SAP-samples/ams-samples-java)](https://api.reuse.software/info/github.com/SAP-samples/ams-samples-java)

**Note:**  
Each sample includes its own README with detailed setup, configuration, and deployment instructions.
