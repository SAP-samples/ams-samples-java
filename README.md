# Samples using Authorization Service

## Description
All samples deployed on Cloud Foundry uses the [SAP application router](https://www.npmjs.com/package/@sap/approuter) as OAuth 2.0 client and Business Application Gateway and forwards as reverse proxy the requests to a Spring Boot backend application running on Cloud Foundry. The application uses a [Token Validation Client Library] to validate the token before serving a resource to the client: it checks for all incoming requests whether the user is authenticated. [Authorization Client Library] is used to make sure that the subject has the requested permissions assigned with **Authorization Management Service (AMS)**.

AMS consists of several components, namely 
- the **Authorization Management Service (AMS)**  
that stores and bundles application specific policy and data.
- the **Authorization AddOn**  
that uploads application's base dcl policies to AMS and starts the [open source **Open Policy Agent (OPA)**](https://www.openpolicyagent.org/) as policy decision engine to decide whether a given user has the policy to perform a dedicated action on a dedicated resource. In BTP Clod Foundry environment it is initialized with the [cloud authorization buildpack](https://github.com/SAP/cloud-authorization-buildpack). 

<img src="https://github.wdf.sap.corp/CPSecurity/AMS/blob/master/Overview/images/AMS_BigPicture_CF_simple.drawio.svg" alt="drawing" width="800px"/>

- During application deployment cloud authorization buildpack is responsible to upload the base policies specified by the application to the AMS. Therefore, the ``AMS_DCL_ROOT`` needs to specify the location of the dcls in the environment.
- The administrator enriches the base policies and assigns them to the users. The policy engine pulls the policy bundles from the bundle gateway every 60 seconds and updates the policy engine in case of changes.
- During authorization checks, the application requests the policy engine to request or check the user's permissions and whether the user is allowed to perform the action X on resource Y by optionally considering instance specific attributes.

More detailed description can be found [here](https://github.wdf.sap.corp/pages/CPSecurity/AMS/Overview/AMS_basics/).


## Overview Samples

| Feature                         | Version | [Java](java-security-ams) | [Spring](spring-security-ams) | 
|---------------------------------|---------|---------------------------|-------------------------------|
| unit testing                    |         | x                         | x                             | 
| local setup / testing           |         | x                         | x                             | 
| multi-tenancy                   | 0.8.0   | x                         |                               |                      
| value help (odata)              | 0.9.0   | x                         |                               |                      
| privileged mode for techn. comm | 0.9.0   |                           | x                             |                      
| kyma/kubernetes deployment      | 0.9.0   |                           | x                             |              

# Download and Installation
You need to clone this [Github repository](https://github.wdf.sap.corp/CPSecurity/ams-samples-java) that includes the samples. The used libraries are available in [Jfrog Artifactory on GCP](https://int.repositories.cloud.sap/artifactory/build-releases/com/sap/cloud/security/ams/client/).

# Prerequisites
1. You need a new Cloud Foundry Subaccount (CANARY) that fulfills this criteria:
  - Zone-enabled <br/> To zone enable your subaccount, select it via the SAP BTP Cockpit, goto _**Security --> Trust Configuration**_, choose _**Establish Trust**_, select an identity provider from the list of available ones and choose _**Establish Trust**_ again. See also [Establish Trust][HSCESTR] for further details.
2. For the Java/Spring sample Java 11 is prerequisite!

[HSCESTR]: https://help.sap.com/viewer/65de2977205c403bbc107264b8eccf4b/Cloud/en-US/161f8f0cfac64c4fa2d973bc5f08a894.html
