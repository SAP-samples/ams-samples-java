## Test Locally (Draft)

#### Configure the local environment

This demo application can be tested locally in a hybrid setup. That means that the application, as well as Open Policy
Agent (OPA) runs locally but for token-validation it uses the OAuth2 Identity Service that was created on Cloud Foundry
in the previous step. Perform these steps to adapt your configuration.

1. Get the ``clientid``, the ``domain`` and the `url` from your Identity Service as follows
    ```shell
    cf create-service-key spring-security-ams-identity authn-sk
    cf service-key spring-security-ams-identity authn-sk
    ```
2. Open the [application-local.yml](./src/main/resources/application-local.yml) file and overwrite the
   ``sap.security.services.identity`` properties accordingly.

#### Start application locally

Ensure that your current Maven profile is configured to use SAP Internal corporate network Artifactory as a plugin
repository (e.g. https://int.repositories.cloud.sap/artifactory/build-milestones).

Run your sample Spring Boot application `samples/spring-security-ams` in local-mode in order to start the OPA locally:

```shell
mvn spring-boot:run -Dspring-boot.run.useTestClasspath -Dspring-boot.run.profiles=local
```  

> With `mvn spring-boot:run` the application gets compiled and the [
`dcl-compiler-plugin` maven plugin](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/blob/master/docs/maven-plugins.md#dcl-compiler)
> generates based on the `src/main/resources/ams/*.dcl` files `*.rego` files that can be consumed by OPA.

> With ``-Dspring-boot.run.useTestClasspath`` the OPA policy engine gets started locally, and it gets preconfigured with
> all generated `*.rego` files. The debug logs give you the ``host:port``, the OPA service is started, e.g.
``127.0.0.1:51631``.

> In order to configure ``spring-security`` for hybrid execution we added some ```sap.security.services.identity```
> properties [application-local.yml](/src/main/resources/application.yml) which are only active on ``local``profile.

> Example (debug) logs:
>```shell script
> INFO  29887 --- [main] c.s.c.s.a.d.r.o.s.OpaPdpLocalServer      : Connected to local running OPA with version=0.23.2 (Port=60629, retries=0, duration=240).
> DEBUG 29926 --- [main] a.f.TestServerPolicyDecisionPointFactory : instantiate PolicyDecisionPoint for kind server:opa and args [sources, /Users/me/git/cloud-authorization-client-library-java/samples/spring-security-ams/target/dcl_opa].
>```

#### Test locally

When your application is successfully started (check the console logs) you can perform the following GET-requests with
your http client (e.g. Postman):

- `http://localhost:8080/health` should return "ok" (Status Code `200`). If not please check the application logs
  whether the local OPA Service is unavailable.
- `http://localhost:8080/salesOrders/readByCountry/IT` with a valid token from your identity service. See
  also [here](https://github.com/SAP/cloud-security-xsuaa-integration/blob/main/docs/HowToFetchToken.md) on how to fetch
  a token from identity service.
  This `GET` request tries to execute a secured method. It will respond with error status code `403` (`unauthorized`) in
  case your user does not have any policy assigned, that grants access for action `read` on any resources in `Country` =
  `<your country Code, e.g. 'IT'>`.

#### Assign permission locally

Check the application logs on your console to find out the user id and the zone id and the result of the authorization
check.

```
Derived potential action/resource authorities for 'Principal {zoneId='4b0c2b7a-1279-4352-a68d-a9a228a4f1e9', id='6f3cae35-b391-4af2-9fe7-2395b280de61', policies=[]}': [].
```

In case you have a lack of permissions you need to make sure your user (from `<zone-id>`, `<user-id>`) has the
`common.readAll_Europe` policy assigned.

To fix the missing permissions locally, you can generate a binding from your user to the policy by making use of the [
`dcl-compiler-plugin` maven plugin](/docs/maven-plugins.md#dcl-compiler):

```shell script
mvn dcl-compiler:principalToPolicies -DzoneId=4b0c2b7a-1279-4352-a68d-a9a228a4f1e9 -DprincipalId=d9403e85-2029-46f1-9c09-ee32e881c081 -Dpolicies=common.readAll_Europe,common.viewAll
```

Now restart the application and repeat the forbidden test request.
