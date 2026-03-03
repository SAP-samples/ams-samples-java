package customer.ams_cap_bookshop.customization.config;

import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.security.token.SapIdToken;
import com.sap.cloud.security.token.SecurityContext;
import com.sap.cloud.security.token.XsuaaToken;
import com.sap.cloud.security.xsuaa.jwt.Base64JwtDecoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper class for setting up security contexts in tests.
 *
 * <p>Provides utility methods for:
 * <ul>
 *   <li>Running code with XSUAA tokens</li>
 *   <li>Running code with hybrid IAS + XSUAA tokens (for combined authorizations)</li>
 *   <li>Creating test JWT tokens (both XSUAA and IAS)</li>
 *   <li>Setting up SERVICE_BINDING_ROOT for tests</li>
 * </ul>
 */
public class TestSecurityContextHelper {

    private static final Base64JwtDecoder BASE64_JWT_DECODER = Base64JwtDecoder.getInstance();

    /**
     * Tenant ID for test IAS tokens - used in policy assignment configuration.
     * This constant should be shared between test configuration and JWT creation.
     */
    public static final String TEST_TENANT_ID = "test-tenant-12345678";

    /**
     * Path to service bindings directory for tests.
     */
    public static final String SERVICE_BINDING_ROOT = "src/test/resources/customization/service-bindings";

    private final CdsRuntime cdsRuntime;

    public TestSecurityContextHelper(CdsRuntime cdsRuntime) {
        this.cdsRuntime = cdsRuntime;
    }

    // ===================================================================================
    // Environment Setup
    // ===================================================================================

    /**
     * Sets the SERVICE_BINDING_ROOT environment variable using reflection.
     * This allows tests to configure service bindings without requiring external environment configuration.
     *
     * <p>Requires JVM args for JDK 17+:
     * <pre>
     * --add-opens java.base/java.util=ALL-UNNAMED
     * </pre>
     *
     * <p>Typically called from a static initializer block in test classes.
     */
    @SuppressWarnings("unchecked")
    public static void setServiceBindingRoot() {
        // Skip if already set (e.g., by external configuration)
        if (System.getenv("SERVICE_BINDING_ROOT") != null) {
            return;
        }

        try {
            // Get the unmodifiable environment map and make it modifiable via reflection
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put("SERVICE_BINDING_ROOT", SERVICE_BINDING_ROOT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set SERVICE_BINDING_ROOT environment variable. " +
                    "Ensure JVM args include: --add-opens java.base/java.util=ALL-UNNAMED", e);
        }
    }

    // ===================================================================================
    // Token Context Runners
    // ===================================================================================

    /**
     * Runs the given supplier within a CDS request context with the XSUAA token set.
     *
     * @param jwt      the JWT token string
     * @param supplier the supplier to run
     * @param <T>      the return type
     * @return the result from the supplier
     */
    public <T> T runWithXsuaaToken(String jwt, Supplier<T> supplier) {
        // Set the token in SAP security library context
        XsuaaToken xsuaaToken = new XsuaaToken(jwt);
        SecurityContext.setToken(xsuaaToken);

        // Also set up Spring Security context with a JwtAuthenticationToken
        Jwt springJwt = createSpringJwtFromXsuaa(jwt, xsuaaToken);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(springJwt);
        org.springframework.security.core.context.SecurityContext springSecurityContext =
                SecurityContextHolder.createEmptyContext();
        springSecurityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(springSecurityContext);

        try {
            Function<RequestContext, T> function = ctx -> supplier.get();
            return cdsRuntime.requestContext().run(function);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Runs the given supplier within a CDS request context with hybrid IAS + XSUAA tokens.
     *
     * <p>This sets up the security context for the combinedAuthorizations feature:
     * <ul>
     *   <li>The IAS token is set as the primary token via SecurityContext.setToken()</li>
     *   <li>The XSUAA token is set as the additional token via SecurityContext.setXsuaaToken()</li>
     * </ul>
     *
     * @param iasJwt   the IAS JWT token string (primary token)
     * @param xsuaaJwt the XSUAA JWT token string (additional token for combined authorizations)
     * @param supplier the supplier to run
     * @param <T>      the return type
     * @return the result from the supplier
     */
    public <T> T runWithHybridTokens(String iasJwt, String xsuaaJwt, Supplier<T> supplier) {
        // Create and set the IAS token as the primary token
        SapIdToken iasToken = new SapIdToken(BASE64_JWT_DECODER.decode(iasJwt));
        SecurityContext.setToken(iasToken);

        // Create and set the XSUAA token as the additional token for combined authorizations
        XsuaaToken xsuaaToken = new XsuaaToken(xsuaaJwt);
        SecurityContext.setXsuaaToken(xsuaaToken);

        // Set up Spring Security context with the IAS token
        Jwt springJwt = createSpringJwtFromIas(iasJwt, iasToken);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(springJwt);
        org.springframework.security.core.context.SecurityContext springSecurityContext =
                SecurityContextHolder.createEmptyContext();
        springSecurityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(springSecurityContext);

        try {
            Function<RequestContext, T> function = ctx -> supplier.get();
            return cdsRuntime.requestContext().run(function);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ===================================================================================
    // Spring JWT Creation
    // ===================================================================================

    /**
     * Creates a Spring Security Jwt object from the raw JWT string and XsuaaToken.
     */
    private Jwt createSpringJwtFromXsuaa(String jwt, XsuaaToken xsuaaToken) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(3600);

        Map<String, Object> headers = Map.of("alg", "none", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "sub", xsuaaToken.getClaimAsString("sub"),
                "user_name", xsuaaToken.getClaimAsString("user_name"),
                "user_id", xsuaaToken.getClaimAsString("user_id"),
                "email", xsuaaToken.getClaimAsString("email"),
                "scope", List.of(xsuaaToken.getClaimAsStringList("scope").toArray()),
                "zid", xsuaaToken.getClaimAsString("zid"),
                "ext_attr", Map.of("enhancer", "XSUAA")
        );

        return new Jwt(jwt, issuedAt, expiresAt, headers, claims);
    }

    /**
     * Creates a Spring Security Jwt object from the raw JWT string and SapIdToken (IAS token).
     */
    private Jwt createSpringJwtFromIas(String jwt, SapIdToken iasToken) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(3600);

        Map<String, Object> headers = Map.of("alg", "none", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "sub", iasToken.getClaimAsString("sub"),
                "scim_id", iasToken.getClaimAsString("scim_id"),
                "user_uuid", iasToken.getClaimAsString("user_uuid"),
                "email", iasToken.getClaimAsString("email"),
                "app_tid", iasToken.getAppTid(),
                "iss", iasToken.getClaimAsString("iss"),
                "ias_iss", iasToken.getClaimAsString("ias_iss")
        );

        return new Jwt(jwt, issuedAt, expiresAt, headers, claims);
    }

    // ===================================================================================
    // JWT Creation Methods
    // ===================================================================================

    /**
     * Creates a test XSUAA JWT token with the specified user and scopes.
     *
     * <p>The JWT payload is structured like a typical XSUAA token with:
     * <ul>
     *   <li>Standard identity claims (sub, user_name, email, etc.)</li>
     *   <li>XSUAA-specific claims (ext_attr, zid, scope, etc.)</li>
     *   <li>Configurable scopes for authorization</li>
     * </ul>
     *
     * @param userName the username for the token
     * @param scopes   the scopes to include in the token
     * @return a base64-encoded JWT string
     */
    public static String createXsuaaJwt(String userName, String... scopes) {
        String scopesJson = String.join("\", \"", scopes);

        String payload = String.format("""
                        {
                          "sub": "test-user",
                          "xs.user.attributes": {},
                          "user_name": "%s@example.com",
                          "iss": "https://test-tenant.authentication.eu12.hana.ondemand.com/oauth/token",
                          "xs.system.attributes": {
                            "xs.rolecollections": [
                              "TestRoleCollection"
                            ]
                          },
                          "family_name": "User",
                          "given_name": "Test",
                          "client_id": "sb-bookshop-test!t1234",
                          "aud": [
                            "sb-bookshop-test!t1234",
                            "bookshop!t1234",
                            "openid"
                          ],
                          "ext_attr": {
                            "enhancer": "XSUAA",
                            "subaccountid": "12345678-1234-1234-1234-123456789012",
                            "zdn": "test-tenant"
                          },
                          "user_uuid": "4711",
                          "zid": "98765432-4321-4321-4321-210987654321",
                          "user_id": "test-user",
                          "azp": "sb-bookshop-test!t1234",
                          "scope": ["%s"],
                          "exp": %d,
                          "iat": %d,
                          "jti": "test-jwt-id-12345",
                          "email": "%s@example.com",
                          "cid": "sb-bookshop-test!t1234"
                        }
                        """,
                userName,
                scopesJson,
                System.currentTimeMillis() / 1000 + 3600,  // expires in 1 hour
                System.currentTimeMillis() / 1000,         // issued now
                userName);

        return encodeJwt(payload);
    }

    /**
     * Creates a test IAS (Identity Authentication Service) JWT token.
     *
     * <p>The JWT payload is structured like a typical IAS token with:
     * <ul>
     *   <li>Standard identity claims (sub, email, given_name, family_name)</li>
     *   <li>IAS-specific claims (app_tid, scim_id, user_uuid, ias_iss)</li>
     *   <li>Anonymized UUIDs and tenant IDs for testing</li>
     * </ul>
     *
     * @param userName the username for the token (used in email generation)
     * @param scimId   the SCIM ID (unique user identifier for policy assignment lookup)
     * @return a base64-encoded JWT string
     */
    public static String createIasJwt(String userName, String scimId) {
        String payload = String.format("""
                        {
                          "sub": "%s",
                          "app_tid": "%s",
                          "iss": "https://test-tenant.accounts.ondemand.com",
                          "given_name": "Test",
                          "ias_iss": "https://test-tenant.accounts.ondemand.com",
                          "aud": "test-app-client-id",
                          "scim_id": "%s",
                          "user_uuid": "%s",
                          "azp": "test-app-client-id",
                          "cnf": {
                            "x5t#S256": "test-certificate-thumbprint"
                          },
                          "exp": %d,
                          "iat": %d,
                          "family_name": "User",
                          "jti": "test-ias-jwt-id-12345",
                          "email": "%s@example.com"
                        }
                        """,
                scimId,                                     // sub (same as scim_id for user tokens)
                TEST_TENANT_ID,                             // app_tid (tenant ID)
                scimId,                                     // scim_id (user identifier for policy lookup)
                scimId,                                     // user_uuid
                System.currentTimeMillis() / 1000 + 3600,   // exp (expires in 1 hour)
                System.currentTimeMillis() / 1000,          // iat (issued now)
                userName);                                  // email

        return encodeJwt(payload);
    }

    /**
     * Encodes a JWT payload into a complete JWT string.
     */
    private static String encodeJwt(String payload) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("test-signature".getBytes());

        return header + "." + encodedPayload + "." + signature;
    }
}