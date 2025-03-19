# Authorization Management Service (AMS) for Spring Boot Sample Application
This Spring Boot sample application utilizes the [spring-ams](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master/spring-ams) and [spring-security](https://github.com/SAP/cloud-security-services-integration-library/tree/main/spring-security) client libraries to validate JWT tokens issued by the [SAP Identity service](https://help.sap.com/docs/identity-authentication).
The application uses the [SAP application router](https://www.npmjs.com/package/@sap/approuter) as OAuth 2.0 client and forwards the requests as reverse proxy to a Spring Boot backend application.
The backend application checks for all incoming requests whether the user is authenticated and authorized via [AMS (Authorization Management Service)](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/).

The application declares its authorization model by providing [DCL files](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/DCLLanguage/Declare) to the AMS service.
The upload is handled by a dcl deployer app which can be build as part of the deployment process (see CF deployment of this sample app).
Alternatively, a pre-build deployer image can be used (see the K8s deployment of this sample app).
For a deeper understanding of how the AMS client library operates, refer to the [documentation](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/ClientLibs/Enforce).

## Getting Started
Before deploying the sample app on Kyma/Kubernetes or Cloud Foundry, we need to setup an IAS tenant and [establish trust](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/HowTo_AMSConfig#establish-ias-trust) in the target subaccount.
<details>
<summary>Deployment on Kyma/Kubernetes</summary>

The k8s deployment contains two pods:
* The [first pod](k8s/app.yaml) consists of two containers, one for the app and one for the approuter.
* The [second pod](k8s/policies-deployer-job.yaml) is just for uploading the DCL files to the AMS server.
  It copies the DCL files from the app image via an init container and then runs a container from a pre-build image to do the actual upload.
  This pod is configured to be removed 5 minutes after the job is done.

### Build, tag and push docker images to a repository
:bulb: If you just want to try out the sample application, you can skip this step and use the pre-build docker images.

#### Backend application
On the console change into the `spring-security-ams` folder.
Then use the following commands to build and pushed the spring boot application to a repository:
```bash
mvn clean package
docker build -t <repository>/<backendImage> .
docker push <repository>/<backendImage>
```
The [docker file of the application](Dockerfile) expects the DCL files to be located in the folder `dcldeployer/dcl`.

#### Approuter
Change into the `spring-security-ams/approuter` folder and use the following docker CLI commands to build and push the approuter:
```bash
docker build -t <repository>/<approuterImage> .
docker push <repository>/<approuterImage>
```
The [dockerfile of the approuter](approuter/Dockerfile) does not need to be adapted.
:warning: Don't forget to change back into the `spring-security-ams` folder afterwards.

### Configure the k8s deployment
1. The pre-build policies deployer image [is configured](k8s/policies-deployer-job.yaml) to be pulled from `common.repositories.cloud.sap`.
This requires a user account and an access token that can be generated at https://common.repositories.cloud.sap/ui/user_profile.
The access token then needs to be stored as a [k8s secret](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/#create-a-secret-by-providing-credentials-on-the-command-line) named `common-artifactory` using the k8s CLI:
```bash
kubectl create secret docker-registry common-artifactory --docker-server=cloud-security-integration.common.repositories.cloud.sap --docker-username=<YOUR USERNAME> --docker-password=<YOUR IDENTITY TOKEN> --docker-email=<YOUR EMAIL> -n <YOUR NAMESPACE>
```
1. If you have build and pushed your own app and/or approuter image in the previous step, you need to replace the default image tags specified in [the app configuration](k8s/app.yaml) and [the policies deployer job configuration](k8s/policies-deployer-job.yaml).
If the images were not pushed to `common.repositories.cloud.sap`, you also need to adapt the corresponding `imagePullSecrets`.
:bulb: In case the images are to be pulled from a public repository, no image pull secret is required.
1. Finally, [the identity service instance configuration](k8s/identity-service-instance.yaml) needs to be completed by providing two redirect URLs for the approuter.
If you know the unique shoot name of your Kyma Cluster, then you can simply replace the `SHOOT_NAME` placeholder.
Otherwise, you can deploy only the service and API rule with
```shell script
kubectl apply -f k8s/service.yaml -n <YOUR NAMESPACE>
kubectl apply -f k8s/apirule.yaml -n <YOUR NAMESPACE>
```
and afterwards retrieve the hostname for the redirect URLs from the API rule in the Kyma cluster dashboard.
Alternatively, you can also deploy the whole app twice and retrieve the URLs in between.

### Deploy the application
After successful configuration you can deploy the applications using [kubectl cli](https://kubernetes.io/docs/reference/kubectl/)
```shell script
kubectl apply -f k8s -n <YOUR NAMESPACE>
```
</details>

<details>
<summary>Deployment on Cloud Foundry</summary>

### Configure the CF deployment
First you need to login using the CF CLI:
```shell
cf login -u <YOUR USER> -o <CF ORG of your subaccount> -s <CF SPACE in your subaccount>
```

Adapt the [vars](../vars.yml) file by:
- specifying a unique value for `ID`, e.g. your user id
- setting `LANDSCAPE_APPS_DOMAIN` according to your landscape, e.g. `cfapps.eu12.hana.ondemand.com` for Canary

### Create the identity service instance (with AMS enabled)
You need to manually replace all ``((LANDSCAPE_APPS_DOMAIN))`` and ``((ID))`` placeholders in `ìas-config.json`.
Then use the IAS service broker to create the ``identity`` service instance:
```shell
cf create-service identity application spring-security-ams-identity -c ias-config.json
```
Further information about identity service and its configuration can be found [here](https://github.wdf.sap.corp/CPSecurity/Knowledge-Base/tree/master/08_Tutorials/iasbroker).

### Build and deploy the application
Use maven and the cf CLI to compile, package and push the application to Cloud Foundry:
```shell
mvn clean package
cf push --vars-file ../vars.yml
```
</details>

### Access the application
After successful deployment, the sample application is accessible via browser at:
- `https://spring-security-ams.<<SHOOT_NAME>>.stage.kyma.ondemand.com/` (for the Kubernetes deployment)
- `https://spring-security-ams-<<ID>>.<<LANDSCAPE_APPS_DOMAIN>>` (for the Cloud Foundry deployment)

The approuter redirects you to a login screen for authentication.
If the login fails, it will return an error status code `401`.
Upon successful login, the index page presents a variety of links for convenient access to different endpoints.

#### Assign policies
The index page also contains a direct link to the AMS Admin UI where you can assign policies to a user.
Follow this [guide](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/Manage/UserAssignments) for more details.

Changes should take effect after at most 60 seconds.
A re-login is not required.

#### Troubleshooting
In case you run into any issues running the sample applicatin, a look into the logs might be helpful:
<details>
<summary>Checking logs with Kubernetes</summary>

```shell
kubectl logs -l app=spring-security-ams -n <YOUR NAMESPACE>
```
</details>

<details>
<summary>Checking logs with Cloud Foundry</summary>

```shell
cf logs spring-security-ams --recent
```
</details>

### Cleanup
If you no longer need the sample application, you can free up resources using the Kubernetes CLI or the cf CLI.
<details>
<summary>Cleanup command for Kubernetes</summary>

```shell
kubectl delete -f k8s -n <YOUR NAMESPACE>
```
</details>

<details>
<summary>Cleanup commands for Cloud Foundry</summary>

```shell
cf unbind-service spring-security-ams-backend spring-security-ams-identity
cf unbind-service spring-security-ams-approuter spring-security-ams-identity
cf unbind-service spring-security-ams-dcl-deployer spring-security-ams-identity
cf delete -f spring-security-ams-backend
cf delete -f spring-security-ams-approuter
cf delete -f spring-security-ams-dcl-deployer
cf delete-service -f spring-security-ams-identity
```
</details>

# Further References
- [Cloud Authorization Service Client Library for Spring Boot Applications](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master/spring-ams)
- [Authorization Management Service (AMS) - Basics](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/)
- [Identity Service Broker](https://github.wdf.sap.corp/pages/CPSecurity/sci-dev-guide/docs/BTP/identity-broker)
- [How to fetch Token](https://github.com/SAP/cloud-security-xsuaa-integration/blob/main/docs/HowToFetchToken.md)
- [Draft on local testing](LocalTesting.md)