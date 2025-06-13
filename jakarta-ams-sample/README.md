# Authorization Management Service (AMS) Jakarta EE Sample Application

This Jakarta EE sample application utilizes the [jakarta-ams](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master-1.x/jakarta-ams) and [java-security](https://github.com/SAP/cloud-security-services-integration-library/tree/main/java-security) client libraries to authenticate JWT tokens issued by the [SAP Identity service](https://help.sap.com/docs/identity-authentication) and to authorize access to resources managed by [Authorization Management Service (AMS)](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/).

The application uses the [SAP application router](https://www.npmjs.com/package/@sap/approuter) as OAuth 2.0 client and forwards the requests as reverse proxy to a Jakarta EE backend application. The backend application checks for all incoming requests whether the user is authenticated and authorized.

The authorization model is declared using [DCL files](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/DCLLanguage/Declare), which are uploaded to AMS via a DCL deployer application. The upload can be done during deployment or via a pre-built image.

For a deeper understanding of how the AMS client library operates, refer to the [documentation](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/ClientLibs/Enforce).

---

## Getting Started

Before deploying the sample app, ensure an IAS tenant is available and [trust is established](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/HowTo_AMSConfig#establish-ias-trust) in your SAP BTP subaccount.

### Deployment Options

<details>
<summary><strong>Kyma / Kubernetes</strong></summary>

Deployment is managed using [Helm 3](https://helm.sh/). The helm chart provisions:

- a pod for the Jakarta EE backend
- a pod for the approuter
- a job for uploading DCL files to AMS

See [helmchart](helmchart/) for configuration.

#### Build and Push Images

```bash
mvn clean package
docker build -t <repo>/<backendImage> .
docker push <repo>/<backendImage>

cd approuter
docker build -t <repo>/<approuterImage> .
docker push <repo>/<approuterImage>
cd ..
```

#### Create Docker Secret

```bash
kubectl create secret docker-registry common-artifactory   --docker-server=cloud-security-integration.common.repositories.cloud.sap   --docker-username=<USERNAME>   --docker-password=<TOKEN>   --docker-email=<EMAIL>   -n <YOUR NAMESPACE>
```

#### Deploy via Helm

```bash
helm upgrade --install jakarta-ams-sample ./helmchart   --values ./helmchart/values.yaml   --namespace <YOUR NAMESPACE>
```

</details>

<details>
<summary><strong>Cloud Foundry</strong></summary>

Login to your CF landscape:

```bash
cf login -u <USER> -o <ORG> -s <SPACE>
```

Edit `vars.yml`:
- Set `ID`, e.g. your user ID
- Set `LANDSCAPE_APPS_DOMAIN`
- Set `PROVIDER_SUBDOMAIN`

Replace placeholders in `ias-config.json` and `sms-config.json`.

Create services:

```bash
cf create-service identity application jakarta-ams-identity -c ias-config.json --wait
cf create-service subscription-manager provider jakarta-ams-sms -c sms-config.json --wait
```

Build and push:

```bash
mvn clean package
cf push --vars-file ../vars.yml
```

</details>

---

## Access the Application

After deployment, the application will be available via the approuter URL. It requires login via the configured IAS tenant. Upon successful authentication, the app presents a homepage with links to available endpoints.

### Assigning Policies

To assign user policies, use the AMS Admin UI. Follow the official [guide](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/Manage/UserAssignments) for more details.

---

## Troubleshooting

<details>
<summary><strong>Kubernetes</strong></summary>

```bash
kubectl logs -l app=jakarta-ams-sample -n <YOUR NAMESPACE>
kubectl logs -l app=jakarta-ams-sample,component=approuter -n <YOUR NAMESPACE>
kubectl logs -l app=jakarta-ams-sample,component=backend -n <YOUR NAMESPACE>
```
</details>

<details>
<summary><strong>Cloud Foundry</strong></summary>

```bash
cf logs jakarta-ams-approuter --recent
cf logs jakarta-ams-backend --recent
```
</details>

---

## Cleanup

<details>
<summary><strong>Kubernetes</strong></summary>

```bash
helm uninstall jakarta-ams-sample --namespace <YOUR NAMESPACE>
```
</details>

<details>
<summary><strong>Cloud Foundry</strong></summary>

```bash
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

---

## Further References

- [Authorization Management Service (AMS)](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/)
- [Client Library – Jakarta AMS](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master-1.x/jakarta-ams)
- [SAP Identity Authentication Service](https://help.sap.com/docs/identity-authentication)
