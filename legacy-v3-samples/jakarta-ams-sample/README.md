# Authorization Management Service (AMS) Jakarta EE Sample Application with Multitenancy

This Jakarta EE sample application utilizes
the [jakarta-ams (internal)](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master-1.x/jakarta-ams)
and [java-security](https://github.com/SAP/cloud-security-services-integration-library/tree/main/java-security) client
libraries to authenticate JWT tokens issued by
the [SAP Identity service](https://help.sap.com/docs/identity-authentication) and to authorize resource access managed
by [AMS (Authorization Management Service)](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/).
The application uses the [SAP application router](https://www.npmjs.com/package/@sap/approuter) as OAuth 2.0 client and
forwards the requests as reverse proxy to a Jakarta EE backend application.
The backend application checks for all incoming requests whether the user is authenticated and authorized via AMS (
Authorization Management Service)

The sample is deployed in a provider subaccount and subscribed to from a consumer subaccount.
The latter is used to access the application and needs to reside in the same region as the provider subaccount.

The application declares its authorization model by providing DCL files to the AMS service.
The upload is handled by a dcl deployer app which can be build as part of the deployment process (see CF deployment of
this sample app).
Alternatively, a pre-build deployer image can be used (see k8s deployment of this sample app).
For a deeper understanding of how the AMS client library operates, refer to
the [internal SAP documentation](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/ClientLibs/Enforce).

## Getting Started

Before deploying the sample app on Kyma/Kubernetes or Cloud Foundry, we need to setup an IAS tenant and establish trust
in the provider subaccount.

<details>
<summary>Deployment on Kyma/Kubernetes</summary>

The k8s deployment is done via a [helm 3 chart](helmchart) and contains three pods:

* The [first pod](helmchart/templates/backend.yaml) is used to run the backend app (i.e. the actual sample application).
* The [second pod](helmchart/templates/approuter.yaml) contains only the approuter.
* The [third pod](helmchart/templates/policies-deployer-job.yaml) is just for uploading the DCL files to the AMS server.
  It copies the DCL files from the backend app image via an init container and then runs a container from a pre-build
  image to do the actual upload.
  This pod is configured to be removed 5 minutes after the job is done.

### Build, tag and push docker images to a repository

:bulb: If you just want to try out the sample application, you can skip this step and use the pre-build docker images.

Make sure that you are logged in to the docker registry you want to push the images to:

```bash
docker login <repository>
```

#### Backend application

On the console, change into the `jakarta-ams-sample` folder.
Then use the following commands to build and push the backend application to a repository:

```bash
mvn clean package
docker build -t <repository>/<backendImage> .
docker push <repository>/<backendImage>
```

The DCL files are located in the folder `dcldeployer/dcl`.
The reason for this is that the CF deployment builds its own DCL deployer from the `dcldeployer` folder which expects
the DCL files in that location.
For the k8s deployment, you can choose another location.
You then just need to adapt the COPY command in the [docker file of the application](Dockerfile) to the new location.

#### Approuter

Change into the `jakarta-ams-sample/approuter` folder and use the following docker CLI commands to build and push the
approuter:

```bash
docker build -t <repository>/<approuterImage> .
docker push <repository>/<approuterImage>
```

The [dockerfile of the approuter](approuter/Dockerfile) does not need to be adapted.

:warning: Don't forget to change back into the `jakarta-ams-sample` folder afterwards.

### Configure the k8s deployment

1. The [helm chart](helmchart) contains a [values.yaml](helmchart/values.yaml) file which can be used to configure the
   deployment.
   The most important property is the `clusterDomain` which needs to be adapted before installing the helm chart.
   For a cluster resulting from enabling Kyma in your BTP subaccount you can derive the domain from your cluster's shoot
   name like this:
    ```
    <SHOOT_NAME>.stage.kyma.ondemand.com
    ```
   If you don't know the shoot name, you can deploy the chart twice and retrieve the cluster domain from the created API
   rules (e.g. in the Kyma cluster dashboard).
1. The pre-build policies deployer image [is configured](helmchart/templates/policies-deployer-job.yaml) to be pulled
   from `common.repositories.cloud.sap`.
   This requires a user account and an access token that can be generated
   at https://common.repositories.cloud.sap/ui/user_profile.
   The access token then needs to be stored as
   a [k8s secret](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/#create-a-secret-by-providing-credentials-on-the-command-line)
   named `common-artifactory` using the k8s CLI:
    ```bash
    kubectl create secret docker-registry common-artifactory --docker-server=cloud-security-integration.common.repositories.cloud.sap --docker-username=<YOUR USERNAME> --docker-password=<YOUR IDENTITY TOKEN> --docker-email=<YOUR EMAIL> -n <YOUR NAMESPACE>
    ```
   :bulb: You can customize the secrets to be used by adapting the `imagePullSecrets` property in
   the [values.yaml](helmchart/values.yaml) file.
1. If you have build and pushed your own app and/or approuter image in the previous step, you need to replace the
   default image specification in the [values.yaml](helmchart/values.yaml) file.
   If the images were not pushed to `common.repositories.cloud.sap`, you also need to adapt the corresponding
   `imagePullSecrets`.

   :bulb: In case the images are to be pulled from a public repository, no image pull secret is required.
1. Finally, you can configure any subaccount from which the sample application should be reachable.
   For your provider subaccount, i.e. the subaccount where you are running the application, this will work
   out-of-the-box.
   If you already know any subaccount from which you plan
   to [subscribe to the application](#subscribe-to-the-app-from-another-subaccount), you can already configure those as
   well.
   Simply add all corresponding subaccount subdomains to the `subscription.subdomains` property in
   the [values.yaml](helmchart/values.yaml) file.

   :bulb: The subdomains can for example be found in the BTP Cockpit in the Overview section.

### Deploy the application

After successful configuration you can deploy the applications using [helm](https://helm.sh/)

```shell script
helm upgrade --install jakarta-ams-sample ./helmchart --values ./helmchart/values.yaml --namespace <YOUR NAMESPACE>
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

- specifying a unique value for `ID`, e.g. your user id.

  This is used to create unique resources like service instances and routes.
- setting `LANDSCAPE_APPS_DOMAIN` according to your landscape, e.g. `cfapps.eu12.hana.ondemand.com` for Canary.
- providing the subdomain of your provider subaccount (where the app is deployed) as `PROVIDER_SUBDOMAIN`.

Finally, you need to manually replace all ``((LANDSCAPE_APPS_DOMAIN))`` and ``((ID))`` placeholders in
the [ias](ias-config.json) and [sms](sms-config.json) configurations.
The placeholders in the [manifest](manifest.yml) will be replaced automatically during the deployment.

### Create the identity service instance (with AMS enabled)

Use the IAS service broker to create the ``identity`` service instance:

```shell
cf create-service identity application jakarta-ams-identity -c ias-config.json --wait
```

Further information about identity service and its configuration can be
found [here](https://github.wdf.sap.corp/CPSecurity/Knowledge-Base/tree/master/08_Tutorials/iasbroker).

### Create the subscription manager service instance

Use the SMS service broker to create the ``sms`` service instance:

```shell
cf create-service subscription-manager provider jakarta-ams-sms -c sms-config.json --wait
```

Further information about subscription manager service and its configuration can be
found [here](https://int.controlcenter.ondemand.com/index.html#/knowledge_center/articles/7961284168e848efb9e0462e38b4075d).

### Build and deploy the application

Use maven and the cf CLI to compile, package and push the application to Cloud Foundry:

```shell
mvn clean package
cf push --vars-file ../vars.yml
```

</details>

## Subscribe to the app from another subaccount

In SAP BTP Cockpit go to a subaccount which resides in the same region as the provider subaccount where the application
was deployed.
Select `Instances and Subscriptions` from the left pane, click on `Create` button, look for
`Jakarta AMS Multitenancy Sample App (k8s)` or `Jakarta AMS Multitenancy Sample App (CF)` in the dropdown list and click
on `create`.

![subscribe](./cf-cockpit-subscribe.png)

The subscription results in the backend app being called by the subscription manager at
its [subscription callback endpoint](src/main/java/com/sap/cloud/security/samples/CallbackServlet.java).
The callback responds with a subscription URL of the form

```
https://<subscriber subaccount subdomain>-<suffix to make URL unique>.<CF or k8s domain>
```

which is used to access the application in the context of the subscribed subaccount.
The approuter is configured (see [k8s](helmchart/templates/approuter.yaml) and [CF](manifest.yml) deployment) via the
environment variable `TENANT_HOST_PATTERN` to extract the subdomain from this URL.
This information is then used to retrieve the corresponding IAS tenant for authentication and authorization.

For this to work, the approuter needs to be reachable under the subscription URL.
In a productive setup this can be done by using
a [custom domain](https://pages.github.tools.sap/psecrypto/custom-domains/) in combination with a wildcard route.
For our sample deployment, we need to manually reconfigure the approuter.

<details>
<summary>Reconfigure approuter on Kyma/Kubernetes</summary>

For k8s we simply need to:

1. Add the subdomain of the subscribed subaccount to the `subscription.subdomains` property in
   the [values.yaml](helmchart/values.yaml) file.
1. Upgrade the helm chart:
    ```shell
    helm upgrade --install jakarta-ams-sample ./helmchart --values ./helmchart/values.yaml --namespace <YOUR NAMESPACE>
    ```

</details>

<details>
<summary>Reconfigure approuter on Cloud Foundry</summary>

First, retrieve the subscription URL in the BTP Cockpit by clicking on the `Go to Application` button in the details of
the newly created subscription.
This should not work, but open a browser tab with the URL.
In SAP BTP Cockpit navigate to the provider subaccount and select the CF space where you deployed the sample app.
Select Routes from the left pane and create a new route ans use the hostname from the subcription URL retrieved above as
host.
Then map the route to the approuter application.

</details>

## Access the application

After successful subscription, a link under `Go to Application` button will redirect your request to your identity
tenant on the consumer side.
Login with your username and password or wait until single-sign-on has done its magic.
Upon successful login, the index page presents a variety of links for convenient access to different endpoints.

#### Assign policies

The index page also contains a direct link to the AMS Admin UI where you can assign policies to a user.
Changes should take effect after at most 60 seconds.
A re-login is not required.

#### Troubleshooting

In case you run into any issues running the sample application, a look into the logs might be helpful:
<details>
<summary>Checking logs with Kubernetes</summary>

```shell
kubectl logs -l app=jakarta-ams-sample -n <YOUR NAMESPACE>
kubectl logs -l app=jakarta-ams-sample,component=approuter -n <YOUR NAMESPACE>
kubectl logs -l app=jakarta-ams-sample,component=backend -n <YOUR NAMESPACE>
```

</details>

<details>
<summary>Checking logs with Cloud Foundry</summary>

```shell
cf logs jakarta-ams-approuter --recent
cf logs jakarta-ams-backend --recent
```

</details>

### Cleanup

If you no longer need the sample application, you can free up resources using the Kubernetes CLI or the cf CLI.
<details>
<summary>Cleanup command for Kubernetes</summary>

```shell
helm uninstall jakarta-ams-sample --namespace <YOUR NAMESPACE>
```

</details>

<details>
<summary>Cleanup commands for Cloud Foundry</summary>

```shell
cf unbind-service jakarta-ams-backend jakarta-ams-sms --wait
cf unbind-service jakarta-ams-backend jakarta-ams-identity --wait
cf unbind-service jakarta-ams-approuter jakarta-ams-sms --wait
cf unbind-service jakarta-ams-approuter jakarta-ams-identity --wait
cf unbind-service jakarta-ams-dcl-deployer jakarta-ams-identity --wait
cf delete -f jakarta-ams-backend
cf delete -f jakarta-ams-approuter
cf delete -f jakarta-ams-dcl-deployer
cf delete-service -f jakarta-ams-sms
cf delete-service -f jakarta-ams-identity
```

</details>

# Further References

- [How to fetch Token](https://github.com/SAP/cloud-security-xsuaa-integration/blob/main/docs/HowToFetchToken.md)
