package com.sap.cloud.security.ams.samples.auth;

import java.io.*;
import java.nio.file.Paths;
import java.util.Set;

import com.sap.cloud.security.ams.api.*;
import com.sap.cloud.security.ams.core.*;
import com.sap.cloud.security.config.*;
import com.sap.cloud.security.servlet.*;
import com.sap.cloud.security.token.SecurityContext;

import org.slf4j.*;

import io.javalin.http.*;
import io.javalin.security.RouteRole;

/**
 * Javalin handler that showcases authentication and authorization using SAP BTP
 * cloud security and AMS libraries
 */
public class AuthHandler implements Handler {
    private static final Logger LOG = LoggerFactory.getLogger(AuthHandler.class);
    private static final ThreadLocal<Authorizations> authorizationsHolder = new ThreadLocal<>();
    private OAuth2ServiceConfiguration iasCredentials;
    private TokenAuthenticator authenticator;
    protected final AuthorizationManagementService ams;
    protected final IdentityServiceAuthProvider authProvider;

    public AuthHandler() {
        this.setupAuthentication();
        this.ams = this.createAmsClient();
        this.authProvider = this.createAuthProvider();
    }

    protected void setupAuthentication() {
        InputStream input = null;
        try {
            input = new FileInputStream(new File(
                    Paths.get("ams-javalin-shopping", "src", "main", "resources", "vcap_services.json").toString()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        this.iasCredentials = Environments.readFromInput(input).getIasConfiguration();

        // this.iasCredentials = Environments.getCurrent().getIasConfiguration();
        this.authenticator = new IasTokenAuthenticator().withServiceConfiguration(iasCredentials);
    }

    protected AuthorizationManagementService createAmsClient() {
        return AuthorizationManagementServiceFactory.fromIasServiceConfiguration(iasCredentials);
    }

    private static final Set<String> TECHNICAL_USER_APIS = Set.of("GetProducts");
    private static final Set<String> PRINCIPAL_PROPAGATION_APIS = Set.of("GetProducts", "ExternalOrder");

    private IdentityServiceAuthProvider createAuthProvider() {
        return new IdentityServiceAuthProvider(ams)
                .withApiMapper(ApiMapper.ofFunction((String api) -> {
                    if (TECHNICAL_USER_APIS.contains(api)) {
                        return Set.of(String.format("internal.%s", api));
                    } else {
                        return null;
                    }
                }), App2AppFlow.TECHNICAL_USER)
                .withApiMapper(ApiMapper.ofFunction((String api) -> {
                    if (PRINCIPAL_PROPAGATION_APIS.contains(api)) {
                        return Set.of(String.format("internal.%s", api));
                    } else {
                        return null;
                    }
                }), App2AppFlow.RESTRICTED_PRINCIPAL_PROPAGATION);
    }

    public AuthorizationManagementService getAmsClient() {
        return ams;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        LOG.debug("Handling path: {}", ctx.path());

        if (ctx.routeRoles().isEmpty()) {
            LOG.debug("No authentication required for route: {}", ctx.path());
            return;
        }

        this.authenticate(ctx);
        this.authorize(ctx);
    }

    protected void authenticate(Context ctx) {
        TokenAuthenticationResult authenticationResult = authenticator.validateRequest(ctx.req(), ctx.res());
        if (!authenticationResult.isAuthenticated()) {
            LOG.warn("Authentication for route: {} failed: {}", ctx.path(),
                    authenticationResult.getUnauthenticatedReason());
            throw new UnauthorizedResponse();
        }
    }

    private void authorize(Context ctx) {
        if (ctx.routeRoles().contains(Role.AUTHENTICATED) && SecurityContext.getToken() != null) {
            // this means successful authentication is enough for access
            return;
        }

        Authorizations authorizations = authProvider.getAuthorizations();
        authorizationsHolder.set(authorizations);
        for (RouteRole r : ctx.routeRoles()) {
            if (!(r instanceof Role)) {
                LOG.error("Unknown role type: " + r.getClass().getName());
                continue;
            }

            Role role = (Role) r;
            if (!authorizations.checkPrivilege(role.getAction(), role.getResource()).isDenied()) {
                // full or conditional access granted: in the latter case, the filter condition
                // needs to be handled inside the service handler
                return;
            }
        }

        LOG.debug("User has none of the roles that provide access to: {}", ctx.path());
        throw new ForbiddenResponse();
    }

    /**
     * Get the Authorizations for the current request
     *
     * @return Authorizations instance
     */
    public Authorizations getAuthorizations() {
        return authorizationsHolder.get();
    }

    public void clear(Context ctx) {
        SecurityContext.clear();
        authorizationsHolder.remove();
    }
}
