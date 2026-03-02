package customer.ams_cap_bookshop.customization;

import com.sap.cloud.security.ams.api.AuthorizationManagementService;
import com.sap.cloud.security.ams.api.AuthorizationsProvider;
import com.sap.cloud.security.ams.api.ScopeMapper;
import com.sap.cloud.security.ams.cap.api.CdsAuthorizations;
import com.sap.cloud.security.ams.core.HybridAuthorizationsProvider;
import com.sap.cloud.security.ams.dcn.PolicyName;
import com.sap.cloud.security.token.SecurityContext;
import com.sap.cloud.security.token.XsuaaToken;
import com.sap.cloud.security.xsuaa.jwt.Base64JwtDecoder;
import com.sap.cloud.security.xsuaa.jwt.DecodedJwt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Test configuration that demonstrates overriding the default {@link AuthorizationsProvider}
 * with a {@link HybridAuthorizationsProvider} to support XSUAA tokens.
 *
 * <p>This configuration:
 * <ul>
 *   <li>Provides a custom {@link JwtDecoder} that creates {@link XsuaaToken} instances
 *       for XSUAA JWTs and sets them in the {@link SecurityContext}</li>
 *   <li>Overrides the default {@link AuthorizationsProvider} bean with
 *       {@link HybridAuthorizationsProvider} that maps XSUAA scopes to AMS policies</li>
 * </ul>
 */
@TestConfiguration
//@Order(-1000)
public class XsuaaHybridTestConfiguration {

    private static final String XSAPPNAME = "bookshop";

    private final Base64JwtDecoder base64JwtDecoder = Base64JwtDecoder.getInstance();

    /**
     * Overrides the default AuthorizationsProvider with a HybridAuthorizationsProvider
     * that supports mapping XSUAA scopes to AMS policies.
     *
     * <p>The scope mapper maps the following scopes to policies:
     * <ul>
     *   <li>{@code StockManager} -> {@code cap.StockManager}</li>
     *   <li>{@code ContentManager} -> {@code cap.ContentManager}</li>
     * </ul>
     *
     * @param ams the authorization management service
     * @return the HybridAuthorizationsProvider for CdsAuthorizations
     */
    @Bean
    @Primary
    public AuthorizationsProvider<CdsAuthorizations> hybridCdsAuthorizationsProvider(
            AuthorizationManagementService ams) {
        ScopeMapper scopeMapper = ScopeMapper.ofFunctionMultiple(scope -> switch (scope) {
            case "StockManager" -> Set.of(PolicyName.ofSegments("cap", "StockManager"));
            case "ContentManager" -> Set.of(PolicyName.ofSegments("cap", "ContentManager"));
            default -> Set.of();
        });

        return HybridAuthorizationsProvider.create(ams, scopeMapper, CdsAuthorizations::of)
                .withXsAppName(XSAPPNAME);
    }

    /**
     * Override the production JwtDecoder with a test version that decodes
     * JWTs without validation and sets up the SecurityContext with an XsuaaToken.
     */
    @Bean
    @Primary
    public JwtDecoder xsuaaTestJwtDecoder() {
        return token -> {
            try {
                DecodedJwt decodedJwt = base64JwtDecoder.decode(token);
                XsuaaToken xsuaaToken = new XsuaaToken(decodedJwt);
                SecurityContext.setToken(xsuaaToken);

                // Parse claims manually from the decoded JWT payload to avoid JSONObject.toMap() issue
                String payloadJson = decodedJwt.getPayload();
                Map<String, Object> claims = parseJsonToMap(payloadJson);

                // Create simple headers map
                Map<String, Object> headers = Map.of("alg", "none", "typ", "JWT");

                Instant issuedAt = claims.containsKey("iat")
                        ? Instant.ofEpochSecond(((Number) claims.get("iat")).longValue())
                        : Instant.now();
                Instant expiresAt = claims.containsKey("exp")
                        ? Instant.ofEpochSecond(((Number) claims.get("exp")).longValue())
                        : Instant.now().plusSeconds(3600);

                return new Jwt(token, issuedAt, expiresAt, headers, claims);
            } catch (Exception e) {
                throw new JwtException("Failed to decode test XSUAA JWT", e);
            }
        };
    }

    /**
     * Parses a JSON string into a Map using Jackson ObjectMapper.
     * This avoids the org.json.JSONObject.toMap() issue caused by conflicting JSON libraries.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}
