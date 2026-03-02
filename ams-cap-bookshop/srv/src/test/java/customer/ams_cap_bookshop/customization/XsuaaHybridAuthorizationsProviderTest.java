package customer.ams_cap_bookshop.customization;

import cds.gen.adminservice.AdminService;
import cds.gen.adminservice.Authors;
import cds.gen.adminservice.Books;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.security.ams.api.AuthorizationsProvider;
import com.sap.cloud.security.ams.cap.api.CdsAuthorizations;
import com.sap.cloud.security.ams.core.HybridAuthorizationsProvider;
import com.sap.cloud.security.token.SecurityContext;
import com.sap.cloud.security.token.XsuaaToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static cds.gen.adminservice.AdminService_.AUTHORS;
import static cds.gen.adminservice.AdminService_.BOOKS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies the {@link AuthorizationsProvider} bean can be overridden
 * with a {@link HybridAuthorizationsProvider} to support XSUAA tokens.
 *
 * <p>This test demonstrates:
 * <ul>
 *   <li>Overriding the default IAS-based AuthorizationsProvider with HybridAuthorizationsProvider</li>
 *   <li>Using XSUAA JWT tokens for authentication</li>
 *   <li>Mapping XSUAA scopes to AMS policies</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@Import(XsuaaHybridTestConfiguration.class)
class XsuaaHybridAuthorizationsProviderTest {

    /*
     * SERVICE_BINDING_ROOT is set via static initializer using reflection.
     * This requires JVM args: --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED
     * These are configured in pom.xml.
     */
    static {
        setServiceBindingRoot();
    }

    /**
     * Sets the SERVICE_BINDING_ROOT environment variable using reflection.
     * This allows the test to configure service bindings without requiring
     * external environment configuration.
     *
     * <p>Requires JVM args for JDK 17+:
     * <pre>
     * --add-opens java.base/java.util=ALL-UNNAMED
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private static void setServiceBindingRoot() {
        // Skip if already set (e.g., by external configuration)
        if (System.getenv("SERVICE_BINDING_ROOT") != null) {
            return;
        }

        try {
            // Find the service-bindings directory
            String serviceBindingRoot = "src/test/resources/customization/service-bindings";

            // Get the unmodifiable environment map and make it modifiable via reflection
            // System.getenv() returns Collections.unmodifiableMap(new ProcessEnvironment.StringEnvironment(theEnvironment))
            // We need to access the underlying map 'm' in the UnmodifiableMap
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put("SERVICE_BINDING_ROOT", serviceBindingRoot);

        } catch (Exception e) {
            throw new RuntimeException("Failed to set SERVICE_BINDING_ROOT environment variable. " +
                    "Ensure JVM args include: --add-opens java.base/java.util=ALL-UNNAMED", e);
        }
    }

    @Autowired
    private AdminService.Draft adminService;

    @Autowired
    private CdsRuntime cdsRuntime;

    @Autowired
    private AuthorizationsProvider<CdsAuthorizations> authorizationsProvider;

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }
    
    // ===================================================================================
    // StockManager (ManageBooks) tests:
    // - Books: READ + WRITE
    // - Authors: READ only
    // ===================================================================================

    @Test
    void stockManagerCanReadBooks() {
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        assertTrue(result.rowCount() > 0, "StockManager should be able to read books");
    }

    @Test
    void stockManagerCanWriteBooks() {
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        Books book = Books.create();
        book.setTitle("The Tell-Tale Heart");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");  // Edgar Allan Poe
        book.setGenreId(16); // Mystery

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, result.rowCount(), "StockManager should be able to create books");
    }

    @Test
    void stockManagerCanReadAuthors() {
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
        assertTrue(result.rowCount() > 0, "StockManager should be able to read authors");
    }

    @Test
    void stockManagerCannotWriteAuthors() {
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        assertThrows(ServiceException.class, () -> {
            runWithXsuaaToken(jwt, () -> adminService.newDraft(Insert.into(AUTHORS).entry(Collections.emptyMap())));
        }, "StockManager should NOT be able to create authors");
    }

    // ===================================================================================
    // ContentManager (ManageAuthors + ManageBooks via AMS policy) tests:
    // The ContentManager AMS policy grants BOTH ManageAuthors and ManageBooks roles,
    // so ContentManager can write both Books AND Authors.
    // - Books: READ + WRITE (via ManageBooks role from AMS policy)
    // - Authors: READ + WRITE (via ManageAuthors role from AMS policy)
    // ===================================================================================

    @Test
    void contentManagerCanReadBooks() {
        String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        assertTrue(result.rowCount() > 0, "ContentManager should be able to read books");
    }

    @Test
    void contentManagerCanWriteBooks() {
        // ContentManager AMS policy grants ManageBooks role, so write is allowed
        String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

        Books book = Books.create();
        book.setTitle("ContentManager Book");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");
        book.setGenreId(16);

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, result.rowCount(), "ContentManager should be able to create books (via ManageBooks role)");
    }

    @Test
    void contentManagerCanReadAuthors() {
        String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
        assertTrue(result.rowCount() > 0, "ContentManager should be able to read authors");
    }

    @Test
    void contentManagerCanWriteAuthors() {
        String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

        Authors author = Authors.create();
        author.setName("Carlos Ruiz Zafón");
        author.setDateOfBirth(java.time.LocalDate.of(1964, 9, 25));
        author.setPlaceOfBirth("Barcelona, Spain");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(AUTHORS).entry(author)));
        assertEquals(1, result.rowCount(), "ContentManager should be able to create authors");
    }

    // ===================================================================================
    // Combined Scopes tests (both StockManager + ContentManager)
    // ===================================================================================

    @Test
    void combinedScopesCanReadBothEntities() {
        String jwt = createXsuaaJwt("xsuaa-full-access", "bookshop.StockManager", "bookshop.ContentManager");

        Result booksResult = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        assertTrue(booksResult.rowCount() > 0, "User with both scopes should read books");

        Result authorsResult = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
        assertTrue(authorsResult.rowCount() > 0, "User with both scopes should read authors");
    }

    @Test
    void combinedScopesCanWriteBothEntities() {
        String jwt = createXsuaaJwt("xsuaa-full-access", "bookshop.StockManager", "bookshop.ContentManager");

        // Can write books (from StockManager/ManageBooks)
        Books book = Books.create();
        book.setTitle("Combined Access Book");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");
        book.setGenreId(16);
        Result booksResult = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, booksResult.rowCount(), "User with both scopes should create books");

        // Can write authors (from ContentManager/ManageAuthors)
        Authors author = Authors.create();
        author.setName("Combined Access Author");
        author.setDateOfBirth(java.time.LocalDate.of(1970, 1, 1));
        author.setPlaceOfBirth("Test City");
        Result authorsResult = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(AUTHORS).entry(author)));
        assertEquals(1, authorsResult.rowCount(), "User with both scopes should create authors");
    }

    // ===================================================================================
    // No Scopes tests
    // ===================================================================================

    @Test
    void noMatchingScopesDeniesAccessToBooks() {
        String jwt = createXsuaaJwt("xsuaa-no-access-user", "openid");

        assertThrows(ServiceException.class, () -> {
            runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        }, "User without relevant scopes should be denied access to books");
    }

    @Test
    void noMatchingScopesDeniesAccessToAuthors() {
        String jwt = createXsuaaJwt("xsuaa-no-access-user", "openid");

        assertThrows(ServiceException.class, () -> {
            runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
        }, "User without relevant scopes should be denied access to authors");
    }

    /**
     * Runs the given supplier within a CDS request context with the XSUAA token set.
     *
     * @param jwt      the JWT token string
     * @param supplier the supplier to run
     * @param <T>      the return type
     * @return the result from the supplier
     */
    private <T> T runWithXsuaaToken(String jwt, Supplier<T> supplier) {
        // Set the token in SAP security library context
        XsuaaToken xsuaaToken = new XsuaaToken(jwt);
        SecurityContext.setToken(xsuaaToken);

        // Also set up Spring Security context with a JwtAuthenticationToken
        Jwt springJwt = createSpringJwt(jwt, xsuaaToken);
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
     * Creates a Spring Security Jwt object from the raw JWT string and XsuaaToken.
     */
    private Jwt createSpringJwt(String jwt, XsuaaToken xsuaaToken) {
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
    private String createXsuaaJwt(String userName, String... scopes) {
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

        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("test-signature".getBytes());

        return header + "." + encodedPayload + "." + signature;
    }
}