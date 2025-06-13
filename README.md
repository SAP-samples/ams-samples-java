# Java Samples for Authorization Management and CAP

This repository contains Java-based sample applications demonstrating **authorization integration** using:

- **Authorization Management Service (AMS)** with Spring Boot and Jakarta EE
- The **SAP Cloud Application Programming Model (CAP)** with authentication and simple role-based access control

Each sample is self-contained and includes its own setup instructions and deployment configurations for Cloud Foundry and/or Kubernetes.

---

## 📁 Samples Overview

| Sample                            | Description                                                        | Tech Stack         |
|----------------------------------|--------------------------------------------------------------------|--------------------|
| [`spring-security-ams-sample`](spring-security-ams-sample) | AMS integration in a Spring Boot application                        | Spring Boot + AMS  |
| [`jakarta-ams-sample`](jakarta-ams-sample)                 | Jakarta EE application secured using AMS                            | Jakarta EE + AMS   |
| [`cap-bookshop-sample`](cap-bookshop-sample)               | Bookshop demo based on CAP with role-based authorization            | CAP Java           |

---

## ✅ Requirements

- SAP BTP Subaccount (Cloud Foundry and/or Kyma enabled)
- SAP Identity Authentication (IAS) tenant
- Java 11+ and Maven
- (Optional) Docker and Kubernetes CLI

---

## 🚀 Quick Start

1. Clone the repository:
   ```bash
   git clone https://github.com/SAP-samples/authorization-samples-java.git
   cd authorization-samples-java
   ```

2. Choose a sample:
   ```bash
   cd spring-security-ams-sample  # or jakarta-ams-sample or cap-bookshop-sample
   ```

3. Follow the specific `README.md` instructions in the chosen folder.

---

## 🧭 Sample Structure

Each folder contains:

- Complete source code
- README with setup and deployment steps
- Sample configurations for IAS, CF or Kyma
- DCL files for AMS-based authorization

---

## 📚 Further References

- [Authorization Management Service (AMS)](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/)
- [SAP CAP Documentation](https://cap.cloud.sap)
- [SAP Identity Authentication Help](https://help.sap.com/docs/identity-authentication)

---

## 📄 License

This project is licensed under the Apache License 2.0 – see the [LICENSE](./LICENSE) file for details.
