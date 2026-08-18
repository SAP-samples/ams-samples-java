# AMS Value Help Sample for CAP Java

This sample demonstrates the usage of value help for AMS in a CAP Java environment. The sample uses the CAP bookshop and activates value help for the `genre` attribute of books, allowing administrators to select valid genre values from a list when creating policies in the AMS admin console.

## @valueHelp annotation in schema.dcl

In your `schema.dcl` file, add the `@valueHelp` annotation to the attribute you want to enable value help for. The `path` property defines the OData entity path that the AMS server will call on your value help service. The `valueField` and `labelField` properties specify which OData response properties to use as the value and human-readable label respectively.

```dcl
@valueHelp: { 
    path: 'Genres',
    valueField: 'name',
    labelField: 'name'
}
genre: String
```

## Value help policy in ams base policies

A base policy is required in `cap/basePolicies.dcl` that administrators can assign to users who need access to the value help endpoint:

```dcl
POLICY ValueHelpReader {
    ASSIGN ROLE ValueHelpReader;
}
```

Additionally, create the file `internal/AMS_ValueHelp.dcl` containing an internal policy that grants the corresponding privileges to the AMS server when it calls the value help endpoint. The name of this `INTERNAL POLICY` **must exactly match** the `value-help-api-name` configured in `mta.yaml`. This is because `value-help-api-name` becomes the `ias_apis` claim value in the app2app token that the AMS server presents to your application. The `App2appAttributesProcessor` then maps that claim to `internal.AMS_ValueHelp`, and this policy determines what authorizations are granted:

```dcl
INTERNAL POLICY AMS_ValueHelp {
    USE cap.ValueHelpReader;
}
```

## ValueHelpService

In parallel to the CDS files for the admin service and the catalog service, add a dedicated `.cds` file for the value help service. This service exposes the entities that provide value help data and must be protected so that only the AMS server (acting on behalf of an authorized administrator) can call it. Protect it with the `ValueHelpReader` role:

```cds
using { sap.capire.bookshop as my } from '../db/schema';

service AmsValueHelpService @(requires: 'ValueHelpReader') {
    @cds.localized: false
    entity Genres as projection on my.Genres;
}
```

The service name `AmsValueHelpService` is what determines the OData path `/odata/v4/AmsValueHelpService/`. The `value-help-url` in `mta.yaml` must point to the actual URL where the service is served — the names do not have to be identical, but the URL must be consistent with whatever CAP derives from the service name.

## App2App attributes processor

When the AMS server calls the value help endpoint, it uses a **principal propagation** token on behalf of the administrator who triggered the value help request. This token contains an `ias_apis` claim listing the API permission groups the AMS server is authorized to consume (e.g. `["AMS_ValueHelp"]`).

The `App2appAttributesProcessor` is a Java SPI implementation that intercepts this token and maps each API permission group to DCL policies via `PolicyAssignmentBuilder`. It uses explicit whitelists to control which APIs are accepted in which flow:

- **`TECHNICAL_USER_APIS`** — APIs allowed for pure technical user calls (no forwarded user). Each API is mapped to `internal.<api>`, granting the authorizations defined in that `INTERNAL POLICY`.
- **`PRINCIPAL_PROPAGATION_APIS`** — APIs allowed for principal propagation calls (forwarded user). Each API is mapped to `internal.<api>` as an upper-limit filter on what the calling application can do on behalf of the forwarded user.

In this sample both sets contain only `AMS_ValueHelp`, which maps to the `INTERNAL POLICY AMS_ValueHelp` in the `internal` DCL package (see `srv/src/main/resources/ams/internal/AMS_ValueHelp.dcl`). That policy uses `cap.ValueHelpReader`, which grants the `ValueHelpReader` role — satisfying the `@(requires: 'ValueHelpReader')` check on `AmsValueHelpService`.

To add support for additional APIs in your own application, add their names to the appropriate set. Any API not listed in either set is ignored even if it appears in the token, which prevents unintended access from unknown callers.

To register the processor, create the file `src/main/resources/META-INF/services/com.sap.cloud.security.ams.api.AttributesProcessor` containing the fully qualified class name of your implementation:

```
customer.cap_java_vh.App2appAttributesProcessor
```

## Configuration in mta.yaml

Add `provided-apis` and an `authorization` block to the `config` section of the IAS (`identity`) service resource. The `value-help-url` **must use the `.cert.` domain** — on Cloud Foundry.

```yaml
resources:
  - name: sample-cap-java-vh-auth
    type: org.cloudfoundry.managed-service
    parameters:
      service: identity
      service-plan: application
      config:
        provided-apis:
          - name: AMS_ValueHelp
            description: Value Help Callback from AMS
            type: public
        authorization:
          enabled: true
          value-help-url: ~{srv-api/srv-cert-url}/odata/v4/AmsValueHelpService/
          value-help-api-name: AMS_ValueHelp
```

> **Important:** The `consumed-services` entry on the AMS admin console's IAS application — which allows the AMS server to request an app2app token targeting your app — is only registered by the IAS service broker at service instance **creation** time. If you add value help configuration to an existing deployment, a simple redeploy is not sufficient. You must delete the service instance and redeploy so that a fresh instance is created:
> ```sh
> cf delete-service <your-auth-service-name> -f
> cf deploy <your-mtar-file>
> ```

