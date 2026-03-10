package com.sap.cloud.security.ams.samples.auth;

import com.sap.cloud.security.ams.api.AuthorizationManagementService;
import com.sap.cloud.security.ams.config.LocalAuthorizationManagementServiceConfig;
import com.sap.cloud.security.token.SapIdToken;
import com.sap.cloud.security.token.SecurityContext;
import com.sap.cloud.security.xsuaa.jwt.Base64JwtDecoder;
import com.sap.cloud.security.xsuaa.jwt.DecodedJwt;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Authentication handler for testing
 */
public class TestAuthHandler extends AuthHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TestAuthHandler.class);
    private final Base64JwtDecoder jwtDecoder;

    public TestAuthHandler() {
        super();
        this.jwtDecoder = Base64JwtDecoder.getInstance();
    }

    @Override
    protected void setupAuthentication() {
        // No-op for test handler
    }

    @Override
    protected void authenticate(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length());
            DecodedJwt decodedJwt = jwtDecoder.decode(token);
            SapIdToken sapIdToken = new SapIdToken(decodedJwt);
            LOG.info("Successfully mocked SecurityContext.");
            SecurityContext.setToken(sapIdToken);
        } else {
            ctx.status(401).result("Unauthorized - Missing or invalid Authorization header");
        }
    }

    @Override
    public AuthorizationManagementService createAmsClient() {
        try {
            LocalAuthorizationManagementServiceConfig amsTestConfig = new LocalAuthorizationManagementServiceConfig()
                    .withPolicyAssignmentsPath(Path.of("src/test/resources/mockPolicyAssignments.json"));
            AuthorizationManagementService ams = AuthorizationManagementService
                    .fromLocalDcn(Path.of("target/generated-test-resources/ams/dcn"), amsTestConfig);

            ams.whenReady().get(3, TimeUnit.SECONDS);

            return ams;
        } catch (TimeoutException e) {
            throw new RuntimeException("AMS test client did not become ready within timeout", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create AMS client", e);
        }
    }
}
