# Welcome to AMS-CAP Sample for Java

Welcome to the sample project showing an application using AMS in CAP.
To build a sample application from scratch, the sample uses a series of `cds add` commands.

Lastest test done with:

- `@sap/cds-dk` 8.6.1
- `node` 20.18.1
- `npm` 10.8.2
- `Maven` 3.9.9
- `Java` 23.0.2
- `mbt` 1.2.26

## Getting Started

For the prerequisites, you can follow the [Initial Setup](https://cap.cloud.sap/docs/get-started/#setup) guide on
capire.
For the deployment to Cloud Foundry, some additional tools
are [required](https://cap.cloud.sap/docs/guides/deployment/to-cf#prerequisites).

## Single tenant bookshop (CF)

1. Create an empty directory for your sample application

```BASH
mkdir <your_sample_ app_name> # st_ams_cap_sample
```

2. Change into the new directory and initialize it as CAP Java application

```BASH
cd <your_sample_app_name> # st_ams_cap_sample
cds init --java
```

3. Now use the [`cds add` command](https://cap.cloud.sap/docs/tools/cds-cli#cds-add) to add the required components.

```BASH
cds add sample,mta,ias,hana,approuter,ams
```

4. Run a Maven build to verify that everything builds correctly.

```BASH
mvn clean verify
```

5. After building the MTA application, it can be deployed to CF

```BASH
mbt build
cf deploy mta_archives/<your_sample_app_name>_1.0.0-SNAPSHOT.mtar
```

6. After a successful deployment, you can access the API of the sample application via the route of the application
   router.
   The first attempt to access will require a login for the used IAS tenant.
   Access to protected resources, e.g., `odata/v4/AdminService/Books`, will be denied because it has no authorizations.
7. To assign a policy to your user, you go to `<your ias tenant>/admin` and search for the name of the sample
   application.
   The name is part of the identity configuration in the `mta.yaml` file. On the tab `Authorization Policies`, you can
   assign your user to an available policy.
   Requests to the protected route are now working.

### AMS artifacts

After these steps, you should have a working single-tenant CAP bookshop sample application that uses IAS for
authentication and AMS for authorization.
The configuration and artifacts relevant to AMS are:

#### identity

The configuration for the identity instance is located in the `mta.yaml` file with the name
`<your_sample_app_name>-auth`.
This flag enables the AMS for your identity instance:a

```YAML
authorization:
    enabled: true
```

The `<your_sample_app_name>-policies-deployer` app is responsible for deploying your AMS base policies to your AMS
instance.
Details are available in
the [AMS documentation](https://github.wdf.sap.corp/pages/CPSecurity/ams-docu/docs/ClientLibs/DeployDcl#ams-policies-deployer-app).

#### AMS runtime

The dependencies containing the AMS runtime are located in the `srv/pom.xml`:

```XML
<dependency>
    <groupId>com.sap.cloud.security.ams.client</groupId>
    <artifactId>jakarta-ams</artifactId>
    <version>${sap.cloud.security.ams.version}</version>
</dependency>

<dependency>
    <groupId>com.sap.cloud.security.ams.client</groupId>
    <artifactId>cap-ams-support</artifactId>
    <version>${sap.cloud.security.ams.version}</version>
</dependency>
```

`jarkarta-ams` provides the required implementation of the Policy Decision Point(PDP), and `cap-ams-support` provides
the implementation for integrating AMS into CAP.

#### AMS policy generation

AMS provides tooling for creating policies. The configuration `<command>build --for ams</command>` enables the
generation of base policies for all [CAP Roles](https://cap.cloud.sap/docs/guides/security/authorization#roles) found in
the CDS model.
For the sample application, it will generate a single base policy containing the `admin` role.

```SQL
// srv/src/main/resources/ams/cap/basePolicies.dcl
POLICY "admin" {
	ASSIGN ROLE "admin";
}
```

### Adding AMS filters

The first step for adding a filter is to create the desired AMS filter attributes in the AMS schema.

```SQL
// srv/src/main/resources/ams/schema.dcl
SCHEMA {
    genre : String
}
```

The next step is to use the AMS filter attributes in the base policies.

```SQL
// srv/src/main/resources/ams/cap/basePolicies.dcl
POLICY Admin {
    ASSIGN ROLE "admin" WHERE genre IS NOT RESTRICTED;
}

POLICY MysteryAdmin {
    USE Admin RESTRICT genre = 'Mystery';
}
```

The final step is to link the AMS filter attributes via CDS annotations to the CDS entity fields in the CDS
model ([AMS Client Library](https://github.wdf.sap.corp/CPSecurity/cloud-authorization-client-library-java/tree/master/cap-ams-support#ams-annotations-in-cds)).

```CDS
// srv/admin-service-auth.cds
using AdminService from './admin-service';

annotate AdminService.Books with @ams.attributes.genre: (genre.name);
```

After redeploying the application, a user with the `MysteryAdmin` policy can only access books with the genre `Mystery`.

### Local development

> Requires AMS Client Library version > 2.4.0. Update manually if not generated by `cds add`.

Two functionalities that the AMS provides in the CF environment need to be replaced for local development.
The first is a compilation of the AMS policies. The second is the assignment of users to policies.
To compile DCL files locally, the AMS provides the `dcl-compiler-plugin`, and it is set in the `srv/pom.xml` like this:

```XML
<plugin>
	<groupId>com.sap.cloud.security.ams.client</groupId>
	<artifactId>dcl-compiler-plugin</artifactId>
	<version>${sap.cloud.security.ams.version}</version>
	<executions>
		<!-- 1. compile DCL files -->
        <execution>
			<id>compile</id>
			<goals>
				<goal>compile</goal>
			</goals>
			<configuration>
				<sourceDirectory>${project.basedir}/src/main/resources/ams</sourceDirectory>
				<dcn>true</dcn>
				<dcnParameter>pretty</dcnParameter>
				<compileTestToDcn>true</compileTestToDcn>
			</configuration>
		</execution>        
	</executions>
</plugin>
```

The `dcl-compiler-plugin` compiles the DCL files found in `/src/main/resources/ams` into DCN in `srv/target/dcl_opa`.
The assignments of the policies to the mock users is done in the `application.yaml`, where the mock users are defined.

```YAML
mock.users:
      admin:
        password: admin
        policies: 
        	- "cap.Admin"
        attributes:
          businessPartner:
            - "10401010"
      user:
        password: user
        policies:
        	- "local.MysteryAdmin"
        attributes:
```

Put the policies in the `local` package to simulate admin policies for testing. This package is ignored during the
deployment.
The last step is to configure that the local compiled test sources are used by the PDP. Until `3.0.0` This is done
by adding

```YAML
cds:
  security:
    authorization:
      ams:
        test-sources: "" # empty will use the default srv/target/dcl_opa
```

to the relevat profile in the `application.yaml`.
From `3.0.0` it is not required to set the `test-sources` property. Unless, the polcies are stored in 
a different directory. If mock users are maintained for a certain profile, the policy assignment via mock 
users is active by default. 


Now, everything is set up for local development. The AMS policies can be compiled and assigned to the mock users.
Starting the application is now done with:

```BASH
mvn spring-boot:run
```