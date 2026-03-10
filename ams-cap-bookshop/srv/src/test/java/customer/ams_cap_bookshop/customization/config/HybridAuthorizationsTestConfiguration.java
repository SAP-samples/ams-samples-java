package customer.ams_cap_bookshop.customization.config;

import com.sap.cloud.security.ams.api.*;
import com.sap.cloud.security.ams.cap.api.CdsAuthorizations;
import com.sap.cloud.security.ams.core.HybridAuthorizationsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static customer.ams_cap_bookshop.customization.config.TestSecurityContextHelper.TEST_TENANT_ID;

/**
 * Test configuration for testing {@link HybridAuthorizationsProvider} features.
 *
 * <p>This configuration supports both XSUAA-only and combined authorization scenarios
 * through the {@code ams.combined-authorizations-enabled} property.
 *
 * <p>Usage:
 * <ul>
 *   <li>XSUAA-only tests: Use {@code @TestPropertySource(properties = "ams.combined-authorizations-enabled=false")}</li>
 *   <li>Combined authorization tests: Use {@code @TestPropertySource(properties = "ams.combined-authorizations-enabled=true")}</li>
 * </ul>
 *
 * <p>The configuration provides:
 * <ul>
 *   <li>A {@link HybridAuthorizationsProvider} with configurable combined authorizations</li>
 *   <li>Mock {@link PolicyAssignments} for IAS users (used when combined authorizations is enabled)</li>
 *   <li>Scope mapping from XSUAA scopes to AMS policies</li>
 * </ul>
 */
@TestConfiguration
public class HybridAuthorizationsTestConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HybridAuthorizationsTestConfiguration.class);
    private static final String XSAPPNAME = "bookshop";

    @Bean
    @Primary
    public AuthorizationsProvider<CdsAuthorizations> hybridCdsAuthorizationsProvider(
            AuthorizationManagementService ams,
            @Value("${ams.combined-authorizations-enabled:false}") boolean combinedAuthorizationsEnabled) {

        LOG.info("Creating HybridAuthorizationsProvider with combinedAuthorizationsEnabled={}", 
                combinedAuthorizationsEnabled);

        ScopeMapper scopeMapper = ScopeMapper.ofFunctionMultiple(scope -> switch (scope) {
            case "StockManager" -> Set.of(PolicyName.ofSegments("cap", "StockManager"));
            case "ContentManager" -> Set.of(PolicyName.ofSegments("cap", "ContentManager"));
            default -> Set.of();
        });

        return HybridAuthorizationsProvider.create(ams, scopeMapper, CdsAuthorizations::of)
                .withXsAppName(XSAPPNAME)
                .withCombinedAuthorizationsEnabled(combinedAuthorizationsEnabled);
    }
    
    /**
     * Provides mock policy assignments for IAS users.
     * 
     * <p>This bean is used when combined authorizations is enabled to simulate
     * policy assignments that would normally come from the AMS service.
     */
    @Bean
    @Primary
    public PolicyAssignments testPolicyAssignments() {
        LOG.info("Creating test policy assignments for hybrid authorization tests");

        Map<String, Map<String, List<String>>> policiesByTenantAndUser = new HashMap<>();
        Map<String, List<String>> userPolicies = new HashMap<>();

        // User 1: ias-stock-manager - has StockManager policy (ManageBooks only)
        userPolicies.put("12345678-1234-1234-1234-000000000001", List.of("cap.StockManager"));

        // User 2: ias-no-policies - no policies assigned (tests XSUAA scope-only grants)
        userPolicies.put("12345678-1234-1234-1234-000000000002", List.of());

        // User 3: ias-content-manager - has ContentManager policy (ManageBooks + ManageAuthors)
        userPolicies.put("12345678-1234-1234-1234-000000000003", List.of("cap.ContentManager"));

        policiesByTenantAndUser.put(TEST_TENANT_ID, userPolicies);

        LOG.debug("Configured policy assignments for tenant {}: {}", TEST_TENANT_ID, userPolicies);
        return PolicyAssignments.fromTenantUserMap(policiesByTenantAndUser);
    }
}