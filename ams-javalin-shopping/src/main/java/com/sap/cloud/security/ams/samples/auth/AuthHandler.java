package com.sap.cloud.security.ams.samples.auth;

import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.security.ams.api.App2AppFlow;
import com.sap.cloud.security.ams.api.AuthorizationManagementService;
import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.core.IasAuthorizationsProvider;
import com.sap.cloud.security.ams.dcn.PolicyName;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.servlet.IasTokenAuthenticator;
import com.sap.cloud.security.servlet.TokenAuthenticationResult;
import com.sap.cloud.security.servlet.TokenAuthenticator;
import com.sap.cloud.security.token.SecurityContext;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.security.RouteRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

import static com.sap.cloud.security.ams.api.Principal.fromSecurityContext;

/**
 * Javalin handler that showcases authentication and authorization using SAP BTP
 * cloud security and AMS libraries
 */
public class AuthHandler implements Handler {
    private static final Logger LOG = LoggerFactory.getLogger(AuthHandler.class);
    private TokenAuthenticator authenticator;
    protected final AuthorizationManagementService ams;
    protected final IasAuthorizationsProvider<ShoppingAuthorizations> authProvider;

    public AuthHandler() {
        this.setupAuthentication();
        this.ams = this.createAmsClient();
        this.authProvider = this.createAuthProvider();
    }

    protected void setupAuthentication() {
        this.authenticator = new IasTokenAuthenticator().withServiceConfiguration(
                Environments.getCurrent().getIasConfiguration()
        );
    }

    protected AuthorizationManagementService createAmsClient() {
        ServiceBinding identityBinding = DefaultServiceBindingAccessor.getInstance().getServiceBindings().stream()
                .filter(binding -> "identity".equals(binding.getServiceName().orElse(null)))
                .findFirst()
                .orElse(null);

        if (identityBinding == null) {
            throw new IllegalStateException(
                    "No SAP Identity Service credentials found in identityBinding. Refer to the documentation for a local test setup.");
        }

        return AuthorizationManagementService.fromIdentityServiceBinding(identityBinding);
    }

    private static final Set<String> TECHNICAL_USER_APIS = Set.of("GetProducts");
    private static final Set<String> PRINCIPAL_PROPAGATION_APIS = Set.of("GetProducts", "ExternalOrder");

    private IasAuthorizationsProvider<ShoppingAuthorizations> createAuthProvider() {
        return IasAuthorizationsProvider.create(ams, ShoppingAuthorizations::of)
                .withApiMapper((String api, Principal principal) -> {
                    if (TECHNICAL_USER_APIS.contains(api)) {
                        return Set.of(PolicyName.ofSegments("internal", api));
                    } else {
                        return Collections.emptySet();
                    }
                }, App2AppFlow.TECHNICAL_USER)
                .withApiMapper((String api, Principal principal) -> {
                    if (PRINCIPAL_PROPAGATION_APIS.contains(api)) {
                        return Set.of(PolicyName.ofSegments("internal", api));
                    } else {
                        return Collections.emptySet();
                    }
                }, App2AppFlow.FILTERED_PRINCIPAL_PROPAGATION);
    }

    public AuthorizationManagementService getAmsClient() {
        return ams;
    }

    @Override
    public void handle(Context ctx) {
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
        ShoppingAuthorizations authorizations = getAuthorizations();

        if (ctx.routeRoles().contains(Role.AUTHENTICATED) && SecurityContext.getToken() != null) {
            // this means successful authentication is enough for access
            return;
        }

        for (RouteRole r : ctx.routeRoles()) {
            if (!(r instanceof Role role)) {
                LOG.error("Unknown role type: " + r.getClass().getName());
                continue;
            }

            if (!authorizations.checkRole(role).isDenied()) {
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
    public ShoppingAuthorizations getAuthorizations() {
        return authProvider.getAuthorizations(fromSecurityContext());
    }

    public void clear(Context ctx) {
        SecurityContext.clear();
    }
}
