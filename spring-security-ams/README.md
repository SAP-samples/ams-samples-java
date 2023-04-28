<!--
SPDX-FileCopyrightText: 2020 

SPDX-License-Identifier: Apache-2.0
-->
# Description
This sample uses the [SAP application router](https://www.npmjs.com/package/@sap/approuter) as OAuth 2.0 client and forwards as reverse proxy the requests to a Spring Boot backend application running on Cloud Foundry. The application uses [spring security](https://github.com/SAP/cloud-security-xsuaa-integration/tree/main/spring-security) library to validate the OIDC token before serving a resource to the client: it checks for all incoming requests whether the user is authenticated and whether the user has the requested permissions assigned with **Authorization Management Service (AMS)**.

Follow the deployment steps for [Kyma/Kubernetes](#Deployment-on-Kyma/Kubernetes) or [Cloud Foundry](#Deployment-on-Cloud-Foundry).

# Deployment on Kyma/Kubernetes
<details>
<summary>Expand this to follow the deployment steps</summary>

- Build docker image and push to repository
- Configure the deployment.yml
- Deploy the service
- Upload policy data
- Access the service

## Build docker images and push them to repository
The images must be accessible from externally.

#### Spring application
```bash
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=<repositoryName>/<imageName>
docker push <repositoryName>/<imageName>
```

#### Approuter
```bash
docker build -t <repositoryName>/<imageName> -f ./Dockerfile . 
docker push <repositoryName>/<imageName>
```
> This makes use of `approuter/Dockerfile`.

## Configure the deployment.yml
In `deployment.yml` replace the image repository placeholders `<YOUR IMAGE REPOSITORY>` and `<YOUR APPROUTER IMAGE REPOSITORY` with the one created in the previous step.

                                                                                                   
For access to ADC side-car image you will need to have a user for https://common.repositories.cloud.sap and [create an image pull secret](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/#create-a-secret-by-providing-credentials-on-the-command-line) with `--docker-server=cloud-security-integration.common.repositories.cloud.sap` parameter.
Then replace the image pull secret placeholder `<YOUR IMAGE PULL SECRET>` with your just created secret name in the deplyment.yml                                                                                                  

## Deploy the application
Deploy the applications using [kubectl cli](https://kubernetes.io/docs/reference/kubectl/)
```shell script
kubectl apply -f ./k8s/deployment.yml -n <YOUR NAMESPACE>
```

## Upload policy data
Base DCL policies need to be uploaded using a kubectl plugin:
https://github.wdf.sap.corp/CPSecurity/kubectl-dcl.

So for example when in `spring-security-ams` directory of this repository:
```sh
kubectl dcl upload identity src/main/resources --namespace <YOUR NAMESPACE>
```

## Access the application
After successful deployment, we access our spring sample application via approuter.

The url of the exposed service can be found in <br>Kyma Console - ``<Your Namenspace>`` - Discovery and Network - API Rules - ```<spring-security-ams-api>```.

![](/docs/images/KymaConsole_APIRule.png)

First, we need to enter the service url as callback within our ias application as depicted in the screenshot below.

![](/docs/images/IAS_OpenIDConnectConfiguration.png)

For that login as admin to your ias tenant (you can find the identity service``url`` in the ``identity`` secrets), select your application and configure manually these urls as redirect urls within the OpenID Connect Configuration:

- ```<spring-security-ams-api>```/login/callback?authType=ias
- ```<spring-security-ams-api>```/login/callback

Now we can call the url to access the application
- ```<spring-security-ams-api>```/health <br>should return "ok" (Status Code `200`). If not check the application logs ( e.g. using `cf logs spring-security-ams --recent`), whether the AMS Service is unavailable (search for `/v1/data/dcr._default_/ping`).
- ```<spring-security-ams-api>```<br>  
It redirects you to a login-screen to authenticate yourself. <br>It will respond with error status code ``401`` in case of failing logon. <br>After a successful logon the index page provides a list of links to access the different endpoints conveniently.

#### Assign permissions
The created `identity` secret contains a direct link to the AMS Admin UI to assign the missing permissions.

![](/docs/images/KymaConsole_Secrets.png)

After a delay of maximum 60 seconds, repeat the forbidden test request. A re-login is not required.

<br>Please follow the [Guide](https://github.wdf.sap.corp/pages/CPSecurity/AMS/Overview/HowTo_AMSConfig/#create-an-admin-user-for-your-ams-tenant) on how to setup the IAS tenant.  

## Cleanup
Finally, delete your application and your service instances using the following command:
```shell script
 kubectl delete -f ./k8s/deployment.yml -n <YOUR NAMESPACE>
```
 </details>






# Deployment on Cloud Foundry

On Cloud Foundry your application gets deployed together with [AMS buildpack](https://github.com/SAP/cloud-authorization-buildpack), so that the policy decision runtime is available as a *sidecar*. In the manifest you may need to adapt the ``appName`` and `directories` to your `AMS_DCL_ROOT`. The `directories` serves the `.dcl` files and the data which contains the assignments of users to policies.

## Create the OAuth2 identity service instance (with AMS enabled)
Make sure you are in the project root of the spring sample. You need to replace all ``((LANDSCAPE_APPS_DOMAIN))`` and ``((ID))`` placeholders with your d/c/i-User.
Use the ias service broker and create an ``identity`` service instance:
```shell
cf create-service identity application spring-ams-ias -c ias-config.json
```
Further information about identity service and its configuration can be found [here](https://github.wdf.sap.corp/CPSecurity/Knowledge-Base/tree/master/08_Tutorials/iasbroker).

## Test Locally
#### Configure the local environment
This demo application can be tested locally in a hybrid setup. That means that the application, as well as Open Policy Agent (OPA) runs locally but for token-validation it uses the OAuth2 Identity Service that was created on Cloud Foundry in the previous step. Perform these steps to adapt your configuration.

1. Get the ``clientid``, the ``domain`` and the `url` from your Identity Service as follows
    ```shell
    cf create-service-key spring-ams-ias authn-sk
    cf service-key spring-ams-ias authn-sk
    ```
1. Open the [application-local.yml](./src/main/resources/application-local.yml) file and overwrite the ``sap.security.services.identity`` properties accordingly.

#### Start application locally
Ensure that your current Maven profile is configured to use SAP Internal corporate network Artifactory as a plugin repository (e.g. https://int.repositories.cloud.sap/artifactory/build-milestones).

Run your sample Spring Boot application (`samples/spring-security-ams) in local-mode in order to start the OPA locally: 
```
mvn spring-boot:run -Dspring-boot.run.useTestClasspath -Dspring-boot.run.profiles=local
```  

> With `mvn spring-boot:run` the application gets compiled and the [`dcl-compiler-plugin` maven plugin](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/blob/master/docs/maven-plugins.md#dcl-compiler) generates based on the `src/main/resources/ams/*.dcl` files `*.rego` files that can be consumed by OPA.   
   
> With ``-Dspring-boot.run.useTestClasspath`` the OPA policy engine gets started locally and it gets preconfigured with all generated `*.rego` files. The debug logs give you the ``host:port``, the OPA service is started, e.g. ``127.0.0.1:51631``.

> In order to configure ``spring-security`` for hybrid execution we added some ```sap.security.services.identity``` properties [application-local.yml](/src/main/resources/application.yml) which are only active on ``local``profile.

> Example (debug) logs:
>```shell script
> INFO  29887 --- [main] c.s.c.s.a.d.r.o.s.OpaPdpLocalServer      : Connected to local running OPA with version=0.23.2 (Port=60629, retries=0, duration=240).
> DEBUG 29926 --- [main] a.f.TestServerPolicyDecisionPointFactory : instantiate PolicyDecisionPoint for kind server:opa and args [sources, /Users/me/git/cloud-authorization-client-library-java/samples/spring-security-ams/target/dcl_opa].
>```

#### Test locally
When your application is successfully started (pls check the console logs) you can perform the following GET-requests with your http client (e.g. Postman):

- `http://localhost:8080/health` should return "ok" (Status Code `200`). If not please check the application logs whether the local OPA Service is unavailable.
- `http://localhost:8080/salesOrders/readByCountry/IT` with a valid token from your identity service. See also [here](https://github.com/SAP/cloud-security-xsuaa-integration/blob/main/docs/HowToFetchToken.md) on how to fetch a token from identity service. 
This GET request tries to execute a secured method. It will respond with error status code `403` (`unauthorized`) in case your user does not have any policy assigned, that grants access for action `read` on any resources in `Country` = `<your country Code, e.g. 'IT'>`.

#### Assign permission locally
Check the application logs on your console to find out the user id and the zone id and the result of the authorization check. 
```
Derived potential action/resource authorities for 'Principal {zoneId='4b0c2b7a-1279-4352-a68d-a9a228a4f1e9', id='6f3cae35-b391-4af2-9fe7-2395b280de61', policies=[]}': [].
```

In case you have a lack of permissions you need to make sure your user (from `<zone-id>`, `<user-id>`) has the `common.readAll_Europe` policy assigned. 

To fix the missing permissions locally, you can generate a binding from your user to the policy by making use of the [`dcl-compiler-plugin` maven plugin](/docs/maven-plugins.md#dcl-compiler):

```shell script
mvn dcl-compiler:principalToPolicies -DzoneId=4b0c2b7a-1279-4352-a68d-a9a228a4f1e9 -DprincipalId=d9403e85-2029-46f1-9c09-ee32e881c081 -Dpolicies=common.readAll_Europe,common.viewAll
```

Now restart the application and repeat the forbidden test request.

## Configure the manifest
The [vars](../vars.yml) contains hosts and paths that need to be adopted. Use your d/c/i-User as ID and use `cfapps.sap.hana.ondemand.com` as `LANDSCAPE_APPS_DOMAIN`.

## Compile and deploy the application
Deploy the application using `cf push`. It will expect 800MB of free memory quota.

```shell
mvn clean package
cf push --vars-file ../vars.yml
```
> Use cf CLI v7. See Upgrading to [cf CLI v7](https://docs.cloudfoundry.org/cf-cli/v7.html).

## Access the application
After successful deployment, we access our spring sample via approuter. 

- `https://spring-ams-web-<<ID>>.<<LANDSCAPE_APPS_DOMAIN>>/health` <br>should return "ok" (Status Code `200`). If not check the application logs ( e.g. using `cf logs spring-security-ams --recent`), whether the AMS Service is unavailable (search for `/v1/data/dcr._default_/ping`).
- `https://spring-ams-web-<<ID>>.<<LANDSCAPE_APPS_DOMAIN>>`   
It redirects you to a login-screen to authenticate yourself. <br>It will respond with error status code ``401`` in case of failing logon. <br>After a successful logon the index page provides a list of links to access the different endpoints conveniently.

#### Assign permissions
The approuter also contains a direct link to the AMS Admin UI to assign the missing permissions. <br>Please follow the [Guide](https://github.wdf.sap.corp/pages/CPSecurity/AMS/Overview/HowTo_AMSConfig/#create-an-admin-user-for-your-ams-tenant) on how to setup the IAS tenant.  

After a delay of maximum 60 seconds, repeat the forbidden test request. A re-login is not required.

> **Note** You can find the link to the AMS user interface in ``VCAP_SERVICES.identity.credentials.authorization_ui_url``.<br>
> It consists of ``https://<yourZone>--<ias tenant aoxk2addh>.authorization.cfapps.sap.hana.ondemand.com``.

## Clean-Up
Finally, delete your application and your service instances using the following commands:
```
cf delete-service-key spring-ams-ias authn-sk
cf delete -f spring-ams
cf delete -f spring-ams-approuter
cf delete-service -f spring-ams-ias
```

# Further References
- [Cloud Authorization Service Client Library for Spring Boot Applications](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master/spring-ams)
- [Authorization Management Service (AMS) - Basics](https://github.wdf.sap.corp/pages/CPSecurity/AMS/Overview/AMS_basics/)
- [Identity Service Broker](https://github.wdf.sap.corp/CPSecurity/Knowledge-Base/tree/master/08_Tutorials/iasbroker)
- [How to fetch Token](https://github.com/SAP/cloud-security-xsuaa-integration/blob/main/docs/HowToFetchToken.md)
