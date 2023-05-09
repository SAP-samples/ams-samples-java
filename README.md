# Samples using Authorization Service

## Description

All samples deployed on Cloud Foundry use the [SAP application router](https://www.npmjs.com/package/@sap/approuter) as
OAuth 2.0 client and Business Application Gateway and forwards (reverse proxy) the requests to the backend sample
application (Spring/Java) running on Cloud Foundry. The application uses a Token Validation Client Library to validate
the token before serving a resource to the client: it checks for all incoming requests whether the user is
authenticated. The Authorization Client Library is used to make sure that the subject has the requested permissions
assigned with **Authorization Management Service (AMS)**.

AMS consists of several components, namely

- the **Authorization Management Service (AMS)**  
  that stores and bundles application specific policy and data.
- the **Authorization AddOn**  
  that uploads application's base dcl policies to AMS and starts the [open source **Open Policy Agent (OPA)
  **](https://www.openpolicyagent.org/) as policy decision engine to decide whether a given user has the policy to
  perform a specific action on a specific resource. In BTP Clod Foundry environment it is initialized with
  the [cloud authorization buildpack](https://github.com/SAP/cloud-authorization-buildpack).

<img src="https://github.wdf.sap.corp/CPSecurity/AMS/blob/master/Overview/images/AMS_BigPicture_CF_simple.drawio.svg" alt="drawing" width="800px"/>

- During application deployment the Cloud Authorization Buildpack is responsible to upload the base policies specified
  by the application to the AMS. Therefore, the environment variable `AMS_DCL_ROOT` (set in manifest.yml) needs to
  specify the location of the dcls in the
  environment.
- The administrator enriches the base policies and assigns them to the users. The policy engine pulls the policy bundles
  from the bundle gateway every 60 seconds and updates the policy engine in case of changes.
- For authorization checks the application sends a requests to the policy engine (sidecar) to check the users
  permissions. I.e. whether the user is allowed to perform the action X on resource Y by optionally considering instance
  specific attributes.

A more detailed description can be found [here](https://github.wdf.sap.corp/pages/CPSecurity/AMS/Overview/AMS_basics/).

## Overview Samples

| Feature                         | Version | [Java](java-security-ams) | [Spring](spring-security-ams) | 
|---------------------------------|---------|---------------------------|-------------------------------|
| unit testing                    |         | x                         | x                             | 
| local setup / testing           |         | x                         | x                             | 
| multi-tenancy                   | 0.8.0   | x                         |                               |                      
| value help (odata)              | 0.9.0   | x                         |                               |                      
| privileged mode for techn. comm | 0.9.0   |                           | x                             |                      
| kyma/kubernetes deployment      | 0.9.0   |                           | x                             |              

## Download and Installation

### Prerequisites

1. A BTP Subaccount (Cloud Foundry enabled)
2. An Identity Authentication (IAS) tenant
3. An established Trust between your BTP Subaccount and your IAS tenant (Security -> Trust
   configuration -> [Establish Trust](https://help.sap.com/docs/btp/sap-business-technology-platform/establish-trust-and-federation-between-uaa-and-identity-authentication))
4. A functioning Java 11 & Maven installation on your local machine

### Deployment

1. Clone this repository to your local machine
2. Run the Wizard deploy.sh (macOS, Linux, Windows WSL)


The used libraries are available in the [SAP Artifactory](https://int.repositories.cloud.sap/artifactory/build-releases/com/sap/cloud/security/ams/client/).

