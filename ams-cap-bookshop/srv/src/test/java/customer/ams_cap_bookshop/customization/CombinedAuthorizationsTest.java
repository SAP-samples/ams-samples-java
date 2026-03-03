package customer.ams_cap_bookshop.customization;

import cds.gen.adminservice.AdminService;
import cds.gen.adminservice.Authors;
import cds.gen.adminservice.Books;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.security.ams.core.HybridAuthorizationsProvider;
import com.sap.cloud.security.token.SecurityContext;
import customer.ams_cap_bookshop.customization.config.HybridAuthorizationsTestConfiguration;
import customer.ams_cap_bookshop.customization.config.TestSecurityContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static customer.ams_cap_bookshop.customization.config.TestSecurityContextHelper.createIasJwt;
import static customer.ams_cap_bookshop.customization.config.TestSecurityContextHelper.createXsuaaJwt;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies the combined authorizations feature of {@link HybridAuthorizationsProvider}.
 *
 * <p>Combined authorizations allows an IAS user to receive authorizations from:
 * <ul>
 *   <li>IAS policy assignments (the primary source)</li>
 *   <li>XSUAA scopes from an additional XSUAA token (secondary source)</li>
 * </ul>
 *
 * <p>This test demonstrates:
 * <ul>
 *   <li>IAS users with assigned policies getting authorizations from AMS</li>
 *   <li>Additional XSUAA scopes contributing extra authorizations in hybrid mode</li>
 *   <li>Combining both sources when {@code combinedAuthorizationsEnabled=true}</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@Import(HybridAuthorizationsTestConfiguration.class)
@TestPropertySource(properties = "ams.combined-authorizations-enabled=true")
class CombinedAuthorizationsTest {

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
    // Test scenario: IAS user has StockManager policy, XSUAA token adds ContentManager scope
    // Combined result should have full access (StockManager + ContentManager = full access)
    // ===================================================================================

    @Test
    void iasUserWithStockManagerPolicyAndXsuaaContentManagerScopeCanWriteAuthors() {
        // IAS user has StockManager policy assigned (can read/write books, only read authors)
        // XSUAA token adds ContentManager scope which grants ManageAuthors role
        // Combined: should be able to write authors

        String iasJwt = createIasJwt("ias-stock-manager", "12345678-1234-1234-1234-000000000001");
        String xsuaaJwt = createXsuaaJwt("xsuaa-content-manager-extra", "bookshop.ContentManager");

        Authors author = Authors.create();
        author.setName("Combined Auth Test Author");
        author.setDateOfBirth(java.time.LocalDate.of(1980, 5, 15));
        author.setPlaceOfBirth("Combined City");

        Result result = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(AUTHORS).entry(author)));
        assertEquals(1, result.rowCount(),
                "IAS user with StockManager policy + XSUAA ContentManager scope should create authors");
    }

    @Test
    void iasUserWithStockManagerPolicyAndXsuaaContentManagerScopeCanReadBooks() {
        String iasJwt = createIasJwt("ias-stock-manager", "12345678-1234-1234-1234-000000000001");
        String xsuaaJwt = createXsuaaJwt("xsuaa-content-manager-extra", "bookshop.ContentManager");

        Result readResult = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Select.from(BOOKS)));
        assertTrue(readResult.rowCount() > 0, "Should be able to read books");
    }

    @Test
    void iasUserWithStockManagerPolicyAndXsuaaContentManagerScopeCanWriteBooks() {
        String iasJwt = createIasJwt("ias-stock-manager", "12345678-1234-1234-1234-000000000001");
        String xsuaaJwt = createXsuaaJwt("xsuaa-content-manager-extra", "bookshop.ContentManager");

        Books book = Books.create();
        book.setTitle("Combined Auth Test Book");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");
        book.setGenreId(16);

        Result writeResult = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, writeResult.rowCount(), "Should be able to write books");
    }

    // ===================================================================================
    // Test scenario: IAS user has no policies, XSUAA token adds StockManager scope
    // Combined result should have StockManager permissions only
    // ===================================================================================

    @Test
    void iasUserWithNoPoliciesAndXsuaaStockManagerScopeCanReadBooks() {
        // IAS user has no policies assigned
        // XSUAA token adds StockManager scope which grants ManageBooks role
        // Combined: should be able to read books via XSUAA scope

        String iasJwt = createIasJwt("ias-no-policies", "12345678-1234-1234-1234-000000000002");
        String xsuaaJwt = createXsuaaJwt("xsuaa-stock-manager-extra", "bookshop.StockManager");

        Result result = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Select.from(BOOKS)));
        assertTrue(result.rowCount() > 0,
                "IAS user with no policies + XSUAA StockManager scope should read books");
    }

    @Test
    void iasUserWithNoPoliciesAndXsuaaStockManagerScopeCanWriteBooks() {
        String iasJwt = createIasJwt("ias-no-policies", "12345678-1234-1234-1234-000000000002");
        String xsuaaJwt = createXsuaaJwt("xsuaa-stock-manager-extra", "bookshop.StockManager");

        Books book = Books.create();
        book.setTitle("XSUAA Scope Only Book");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");
        book.setGenreId(16);

        Result result = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, result.rowCount(),
                "IAS user with no policies + XSUAA StockManager scope should create books");
    }

    @Test
    void iasUserWithNoPoliciesAndXsuaaStockManagerScopeCannotWriteAuthors() {
        String iasJwt = createIasJwt("ias-no-policies", "12345678-1234-1234-1234-000000000002");
        String xsuaaJwt = createXsuaaJwt("xsuaa-stock-manager-extra", "bookshop.StockManager");

        assertThrows(ServiceException.class, () -> securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.newDraft(Insert.into(AUTHORS).entry(Collections.emptyMap()))), "IAS user with no policies + XSUAA StockManager scope should NOT create authors");
    }

    // ===================================================================================
    // Test scenario: IAS user has ContentManager policy, XSUAA token adds no relevant scopes
    // Combined result should have ContentManager permissions from IAS only
    // ===================================================================================

    @Test
    void iasUserWithContentManagerPolicyAndNoXsuaaScopesCanWriteAuthors() {
        String iasJwt = createIasJwt("ias-content-manager", "12345678-1234-1234-1234-000000000003");
        String xsuaaJwt = createXsuaaJwt("xsuaa-no-scopes", "openid");

        Authors author = Authors.create();
        author.setName("IAS Policy Only Author");
        author.setDateOfBirth(java.time.LocalDate.of(1975, 8, 20));
        author.setPlaceOfBirth("IAS City");

        Result result = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(AUTHORS).entry(author)));
        assertEquals(1, result.rowCount(),
                "IAS user with ContentManager policy + no XSUAA scopes should create authors");
    }

    @Test
    void iasUserWithContentManagerPolicyAndNoXsuaaScopesCanWriteBooks() {
        String iasJwt = createIasJwt("ias-content-manager", "12345678-1234-1234-1234-000000000003");
        String xsuaaJwt = createXsuaaJwt("xsuaa-no-scopes", "openid");

        Books book = Books.create();
        book.setTitle("IAS Policy Only Book");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");
        book.setGenreId(16);

        Result result = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, result.rowCount(),
                "IAS user with ContentManager policy + no XSUAA scopes should create books");
    }

    // ===================================================================================
    // Test scenario: Both IAS and XSUAA contribute different policies - full combined access
    // ===================================================================================

    @Test
    void fullCombinedAccessFromBothTokens() {
        String iasJwt = createIasJwt("ias-stock-manager", "12345678-1234-1234-1234-000000000001");
        String xsuaaJwt = createXsuaaJwt("xsuaa-content-manager-extra", "bookshop.ContentManager");

        // Read books
        Result booksRead = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Select.from(BOOKS)));
        assertTrue(booksRead.rowCount() > 0, "Should read books");

        // Read authors
        Result authorsRead = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Select.from(AUTHORS)));
        assertTrue(authorsRead.rowCount() > 0, "Should read authors");

        // Write books
        Books book = Books.create();
        book.setTitle("Full Combined Access Book");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");
        book.setGenreId(16);
        Result booksWrite = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(BOOKS).entry(book)));
        assertEquals(1, booksWrite.rowCount(), "Should write books");

        // Write authors
        Authors author = Authors.create();
        author.setName("Full Combined Access Author");
        author.setDateOfBirth(java.time.LocalDate.of(1985, 3, 10));
        author.setPlaceOfBirth("Combined Access City");
        Result authorsWrite = securityHelper.runWithHybridTokens(iasJwt, xsuaaJwt, () ->
                adminService.run(Insert.into(AUTHORS).entry(author)));
        assertEquals(1, authorsWrite.rowCount(), "Should write authors");
    }
}