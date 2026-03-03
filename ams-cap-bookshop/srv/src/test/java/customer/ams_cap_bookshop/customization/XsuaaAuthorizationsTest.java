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
import com.sap.cloud.security.ams.core.HybridAuthorizationsProvider;
import com.sap.cloud.security.token.SecurityContext;
import customer.ams_cap_bookshop.customization.config.HybridAuthorizationsTestConfiguration;
import customer.ams_cap_bookshop.customization.config.TestSecurityContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

import static cds.gen.adminservice.AdminService_.AUTHORS;
import static cds.gen.adminservice.AdminService_.BOOKS;
import static customer.ams_cap_bookshop.customization.config.TestSecurityContextHelper.createXsuaaJwt;
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
 *
 * <p>Combined authorizations is disabled (default) - only XSUAA tokens are used.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@Import(HybridAuthorizationsTestConfiguration.class)
@TestPropertySource(properties = "ams.combined-authorizations-enabled=false")
class XsuaaAuthorizationsTest {

    /*
     * SERVICE_BINDING_ROOT is set via static initializer.
     * This requires JVM args: --add-opens java.base/java.util=ALL-UNNAMED
     * These are configured in pom.xml.
     */
    static {
        TestSecurityContextHelper.setServiceBindingRoot();
    }

    @Autowired
    private AdminService.Draft adminService;

    @Autowired
    private CdsRuntime cdsRuntime;

    private TestSecurityContextHelper securityHelper;

    @BeforeEach
    void setUp() {
        securityHelper = new TestSecurityContextHelper(cdsRuntime);
    }

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }

    // ===================================================================================
    // XSUAA-only Tests (Pure XSUAA token scenarios)
    // ===================================================================================

    @Nested
    class XsuaaOnlyTests {

        // ===================================================================================
        // StockManager (ManageBooks) tests:
        // - Books: READ + WRITE
        // - Authors: READ only
        // ===================================================================================

        @Test
        void stockManagerCanReadBooks() {
            String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
            assertTrue(result.rowCount() > 0, "StockManager should be able to read books");
        }

        @Test
        void stockManagerCanWriteBooks() {
            String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

            Books book = Books.create();
            book.setTitle("The Tell-Tale Heart");
            book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");  // Edgar Allan Poe
            book.setGenreId(16); // Mystery

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
            assertEquals(1, result.rowCount(), "StockManager should be able to create books");
        }

        @Test
        void stockManagerCanReadAuthors() {
            String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
            assertTrue(result.rowCount() > 0, "StockManager should be able to read authors");
        }

        @Test
        void stockManagerCannotWriteAuthors() {
            String jwt = createXsuaaJwt("xsuaa-stock-manager", "bookshop.StockManager");

            assertThrows(ServiceException.class, () -> securityHelper.runWithXsuaaToken(jwt, () -> adminService.newDraft(Insert.into(AUTHORS).entry(Collections.emptyMap()))), "StockManager should NOT be able to create authors");
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

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
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

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
            assertEquals(1, result.rowCount(), "ContentManager should be able to create books (via ManageBooks role)");
        }

        @Test
        void contentManagerCanReadAuthors() {
            String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
            assertTrue(result.rowCount() > 0, "ContentManager should be able to read authors");
        }

        @Test
        void contentManagerCanWriteAuthors() {
            String jwt = createXsuaaJwt("xsuaa-content-manager", "bookshop.ContentManager");

            Authors author = Authors.create();
            author.setName("Carlos Ruiz Zafón");
            author.setDateOfBirth(java.time.LocalDate.of(1964, 9, 25));
            author.setPlaceOfBirth("Barcelona, Spain");

            Result result = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(AUTHORS).entry(author)));
            assertEquals(1, result.rowCount(), "ContentManager should be able to create authors");
        }

        // ===================================================================================
        // Combined Scopes tests (both StockManager + ContentManager)
        // ===================================================================================

        @Test
        void combinedScopesCanReadBothEntities() {
            String jwt = createXsuaaJwt("xsuaa-full-access", "bookshop.StockManager", "bookshop.ContentManager");

            Result booksResult = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS)));
            assertTrue(booksResult.rowCount() > 0, "User with both scopes should read books");

            Result authorsResult = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS)));
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
            Result booksResult = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(BOOKS).entry(book)));
            assertEquals(1, booksResult.rowCount(), "User with both scopes should create books");

            // Can write authors (from ContentManager/ManageAuthors)
            Authors author = Authors.create();
            author.setName("Combined Access Author");
            author.setDateOfBirth(java.time.LocalDate.of(1970, 1, 1));
            author.setPlaceOfBirth("Test City");
            Result authorsResult = securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Insert.into(AUTHORS).entry(author)));
            assertEquals(1, authorsResult.rowCount(), "User with both scopes should create authors");
        }

        // ===================================================================================
        // No Scopes tests
        // ===================================================================================

        @Test
        void noMatchingScopesDeniesAccessToBooks() {
            String jwt = createXsuaaJwt("xsuaa-no-access-user", "openid");

            assertThrows(ServiceException.class, () -> securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(BOOKS))), "User without relevant scopes should be denied access to books");
        }

        @Test
        void noMatchingScopesDeniesAccessToAuthors() {
            String jwt = createXsuaaJwt("xsuaa-no-access-user", "openid");

            assertThrows(ServiceException.class, () -> securityHelper.runWithXsuaaToken(jwt, () -> adminService.run(Select.from(AUTHORS))), "User without relevant scopes should be denied access to authors");
        }
    }
}