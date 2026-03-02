package customer.ams_cap_bookshop.customization;

import cds.gen.adminservice.AdminService;
import cds.gen.adminservice.Authors;
import cds.gen.adminservice.Books;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.ServiceException;
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

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    // Note: SERVICE_BINDING_ROOT is set via Maven Surefire plugin in pom.xml
    // to point to src/test/resources/customization/service-bindings

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

    @Test
    void authorizationsProviderIsHybridAuthorizationsProvider() {
        // Verify the AuthorizationsProvider bean was overridden with HybridAuthorizationsProvider
        assertInstanceOf(HybridAuthorizationsProvider.class, authorizationsProvider,
                "AuthorizationsProvider should be HybridAuthorizationsProvider");
    }

    @Test
    void xsuaaTokenIsSetInSecurityContext() {
        String jwt = createXsuaaJwt("test-user", "bookshop.StockManager");
        setXsuaaTokenInSecurityContext(jwt);

        // Verify that XsuaaToken was set in SecurityContext
        assertInstanceOf(XsuaaToken.class, SecurityContext.getToken(),
                "SecurityContext should contain XsuaaToken");
    }

    @Test
    void stockManagerScopeAllowsBooksAccess() {
        // User with StockManager scope can access and manage books
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        assertTrue(result.rowCount() > 0, "StockManager should be able to access books");
    }

    @Test
    void contentManagerScopeAllowsAuthorsAccess() {
        // User with ContentManager scope can access and manage authors
        String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
        assertTrue(result.rowCount() > 0, "ContentManager should be able to access authors");
    }

    @Test
    void stockManagerScopeDoesNotAllowAuthorCreation() {
        // User with only StockManager scope cannot create authors
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        assertThrows(ServiceException.class, () -> {
            runWithXsuaaToken(jwt, () -> adminService.newDraft(Insert.into(AUTHORS).entry(Collections.emptyMap())));
        }, "StockManager should not be able to create authors");
    }

    @Test
    void noMatchingScopesDeniesAccess() {
        // User without relevant scopes is denied access
        String jwt = createXsuaaJwt("xsuaa-no-access-user", "openid");

        assertThrows(ServiceException.class, () -> {
            runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        }, "User without relevant scopes should be denied access to books");
    }

    @Test
    void multipleScopesGrantCombinedPermissions() {
        // User with both StockManager and ContentManager scopes can access both resources
        String jwt = createXsuaaJwt("xsuaa-full-access", "bookshop.StockManager", "bookshop.ContentManager");

        // Can access books
        Result booksResult = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
        assertTrue(booksResult.rowCount() > 0, "User with both scopes should access books");

        // Can access authors
        Result authorsResult = runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
        assertTrue(authorsResult.rowCount() > 0, "User with both scopes should access authors");
    }

    @Test
    void stockManagerCanCreateBooks() {
        String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

        Books book = Books.create();
        book.setTitle("The Tell-Tale Heart");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");  // Edgar Allan Poe
        book.setGenreId(16); // Mystery

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, result.rowCount(), "StockManager should be able to create books");
    }

    @Test
    void contentManagerCanCreateAuthors() {
        String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

        Authors author = Authors.create();
        author.setName("Carlos Ruiz Zafón");
        author.setDateOfBirth(java.time.LocalDate.of(1964, 9, 25));
        author.setPlaceOfBirth("Barcelona, Spain");

        Result result = runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(AUTHORS).entry(author)));
        assertEquals(1, result.rowCount(), "ContentManager should be able to create authors");
    }

    /**
     * Runs the given supplier within a CDS request context with the XSUAA token set.
     *
     * @param jwt      the JWT token string
     * @param supplier the supplier to run
     * @param <T>      the return type
     * @return the result from the supplier
     */
    private <T> T runWithXsuaaToken(String jwt, java.util.function.Supplier<T> supplier) {
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
            java.util.function.Function<com.sap.cds.services.request.RequestContext, T> function = ctx -> supplier.get();
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
     * Sets the XSUAA token in the SecurityContext.
     *
     * @param jwt the JWT token string
     */
    private void setXsuaaTokenInSecurityContext(String jwt) {
        XsuaaToken token = new XsuaaToken(jwt);
        SecurityContext.setToken(token);
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
                          "sub": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
                          "xs.user.attributes": {},
                          "user_name": "%s@example.com",
                          "origin": "sap.custom",
                          "iss": "https://test-tenant.authentication.eu12.hana.ondemand.com/oauth/token",
                          "xs.system.attributes": {
                            "xs.rolecollections": [
                              "TestRoleCollection"
                            ]
                          },
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
                          "user_uuid": "b2c3d4e5-6789-0abc-def1-234567890abc",
                          "zid": "98765432-4321-4321-4321-210987654321",
                          "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                          "user_id": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
                          "azp": "sb-bookshop-test!t1234",
                          "scope": ["%s"],
                          "cnf": {
                            "x5t#S256": "test-certificate-thumbprint"
                          },
                          "exp": %d,
                          "family_name": "User",
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