package com.sap.cloud.security.ams.samples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.Order;
import com.sap.cloud.security.ams.api.Privilege;
import com.sap.cloud.security.spring.token.authentication.JavaSecurityContextHolderStrategy;
import com.sap.cloud.security.token.SapIdToken;
import com.sap.cloud.security.xsuaa.jwt.Base64JwtDecoder;
import com.sap.cloud.security.xsuaa.jwt.DecodedJwt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the production IAS token flow, covering the full API surface
 * of the shopping sample: privilege lookup, product and order reads with
 * instance-based filtering, order creation with per-order attribute checks
 * (including the App2App principal propagation flow), and order deletion.
 *
 * <p>
 * The production {@code JwtDecoder} validates the token but does NOT populate the
 * cloud security {@code SecurityContext}. The token must therefore be established
 * by the {@code jwtAuthenticationConverter} configured in {@code SecurityConfiguration},
 * otherwise the AMS principal is missing and all privilege checks are denied with
 * HTTP 403.
 * </p>
 *
 * <p>
 * The {@code resourceserver-security-spring-boot-starter} is excluded from the test
 * classpath (see {@code maven-surefire-plugin} in the pom). In a real deployment its
 * {@code SecurityContextEnvironmentPostProcessor} activates the
 * {@code JavaSecurityContextHolderStrategy}, which copies the token from the Spring
 * Security context into the cloud security {@code SecurityContext}. The strategy is
 * therefore activated by the {@link SecurityContextStrategyInitializer} below, which
 * runs before the application beans capture the strategy at creation time.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = IasJwtFlowTest.SecurityContextStrategyInitializer.class)
@Import(IasJwtFlowTest.ProductionLikeDecoderConfiguration.class)
class IasJwtFlowTest {

    private static MockMvc mockMvc;
    private static ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SimpleDatabase database;

    @BeforeAll
    static void setUpAll(@Autowired WebApplicationContext wac) {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();

        // Configure ObjectMapper with custom deserializer for Privilege class
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Privilege.class, new StdDeserializer<Privilege>(Privilege.class) {
            @Override
            public Privilege deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                return Privilege.of(node.get("action").asText(), node.get("resource").asText());
            }
        });
        objectMapper.registerModule(module);
    }

    @BeforeEach
    void setUp() {
        // Reset database to initial state before each test to ensure test independence
        database.reset();
    }

    // GET /privileges tests
    @Test
    void privilegesEndpointReturnsPrivilegesOfTheCurrentUser() throws Exception {
        String aliceJwt = loadJwtFromFile("User_alice.json");
        String response = mockMvc.perform(get("/privileges")
                .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Set<Privilege> privileges = objectMapper.readValue(response, new TypeReference<Set<Privilege>>() {
        });

        // Alice has DeleteOrders and CreateOrders policies, which also grant
        // read:orders (via DeleteOrders -> ReadOrders) and read:products (via CreateOrders -> ReadProducts)
        Assertions.assertEquals(Set.of(
                Privilege.of("read", "products"),
                Privilege.of("create", "orders"),
                Privilege.of("delete", "orders"),
                Privilege.of("read", "orders")
        ), privileges);
    }

    @Test
    void privilegesEndpointIsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/privileges"))
                .andExpect(status().isUnauthorized());
    }

    // GET /products tests
    @Test
    void productsEndpointIsAccessibleWithValidIasToken() throws Exception {
        String aliceJwt = loadJwtFromFile("User_alice.json");

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk());
    }

    @Test
    void productsEndpointIsDeniedForUserWithoutReadProductsPrivilege() throws Exception {
        String carolJwt = loadJwtFromFile("User_carol.json");

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void productsEndpointIsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isUnauthorized());
    }

    // GET /orders tests
    @Test
    void ordersEndpointReturnsAllOrdersForUserWithReadOrdersPrivilege() throws Exception {
        String aliceJwt = loadJwtFromFile("User_alice.json");
        String response = mockMvc.perform(get("/orders")
                .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Order> orders = objectMapper.readValue(response, new TypeReference<List<Order>>() {
        });
        Assertions.assertNotNull(orders);
        Assertions.assertEquals(4, orders.size());
    }

    @Test
    void ordersEndpointIsFilteredToOwnOrdersForUserWithReadOwnOrdersPrivilege() throws Exception {
        String bobJwt = loadJwtFromFile("User_bob.json");
        String response = mockMvc.perform(get("/orders")
                .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Order> orders = objectMapper.readValue(response, new TypeReference<List<Order>>() {
        });
        Assertions.assertNotNull(orders);
        Assertions.assertFalse(orders.isEmpty());
        Assertions.assertTrue(orders.stream().allMatch(o -> "bob".equals(o.getCreatedBy())));
    }

    @Test
    void ordersEndpointIsDeniedForUserWithoutReadOrdersPrivilege() throws Exception {
        String carolJwt = loadJwtFromFile("User_carol.json");

        mockMvc.perform(get("/orders")
                .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordersEndpointIsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    // POST /orders tests
    @Test
    void createOrderIsAllowedForUserWithCreateOrdersPrivilege() throws Exception {
        String aliceJwt = loadJwtFromFile("User_alice.json");

        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + aliceJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": 1, \"quantity\": 1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrderIsRestrictedToAccessoriesForUserWithOrderAccessoryPolicy() throws Exception {
        String bobJwt = loadJwtFromFile("User_bob.json");

        // Ordering a non-accessory item (Yubikey, category securityAccessory) is denied
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": 5, \"quantity\": 1}"))
                .andExpect(status().isForbidden());

        // Ordering an accessory item (Cherry Keyboard) is allowed
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": 4, \"quantity\": 1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrderForExternalOrderFlowIsRestrictedByOrderTotal() throws Exception {
        String bobExternalJwt = loadJwtFromFile("RestrictedPrincipalPropagation_bob.json");

        // Order total 160 exceeds the ExternalOrder limit of 100
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobExternalJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": 4, \"quantity\": 4}"))
                .andExpect(status().isForbidden());

        // Wrong product category for this flow (Yubikey, category securityAccessory)
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobExternalJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": 5, \"quantity\": 1}"))
                .andExpect(status().isForbidden());

        // Order within the limit is allowed
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobExternalJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": 4, \"quantity\": 2}"))
                .andExpect(status().isCreated());
    }

    // DELETE /orders/{id} tests
    @Test
    void deleteOrderIsAllowedForUserWithDeleteOrdersPrivilege() throws Exception {
        String aliceJwt = loadJwtFromFile("User_alice.json");

        mockMvc.perform(delete("/orders/1")
                .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOrderIsDeniedForUserWithoutDeleteOrdersPrivilege() throws Exception {
        String bobJwt = loadJwtFromFile("User_bob.json");

        mockMvc.perform(delete("/orders/4")
                .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());
    }

    /**
     * Activates the {@link JavaSecurityContextHolderStrategy} before the application
     * context is refreshed, mirroring the production environment where the
     * {@code SecurityContextEnvironmentPostProcessor} of the
     * {@code resourceserver-security-spring-boot-starter} performs this step.
     * Spring Security filters capture the strategy at bean creation time, so it must
     * be set before beans are instantiated.
     */
    public static class SecurityContextStrategyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            SecurityContextHolder.setContextHolderStrategy(new JavaSecurityContextHolderStrategy());
        }
    }

    @TestConfiguration
    static class ProductionLikeDecoderConfiguration {

        private final Base64JwtDecoder base64JwtDecoder = Base64JwtDecoder.getInstance();

        /**
         * Decodes JWTs without validation and, like the production IAS decoder, does not
         * set up the cloud security SecurityContext. The converter in
         * SecurityConfiguration is the only component that establishes the token.
         */
        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            return token -> {
                try {
                    DecodedJwt decodedJwt = base64JwtDecoder.decode(token);
                    SapIdToken sapIdToken = new SapIdToken(decodedJwt);
                    Map<String, Object> headers = sapIdToken.getHeaders();
                    Map<String, Object> claims = sapIdToken.getClaims();
                    Instant issuedAt = claims.containsKey("iat")
                            ? Instant.ofEpochSecond(((Number) claims.get("iat")).longValue())
                            : Instant.now();
                    Instant expiresAt = Optional.ofNullable(sapIdToken.getExpiration())
                            .orElse(Instant.now().plusSeconds(3600));
                    return new Jwt(token, issuedAt, expiresAt, headers, claims);
                } catch (Exception e) {
                    throw new JwtException("Failed to decode test JWT", e);
                }
            };
        }
    }

    private String loadJwtFromFile(String filename) throws IOException {
        Path filePath = Path.of("src/test/resources/jwt", filename);
        String jsonPayload = Files.readString(filePath);
        return createTestJwt(jsonPayload);
    }

    private String createTestJwt(String jsonPayload) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonPayload.getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("test-signature".getBytes());
        return header + "." + payload + "." + signature;
    }
}
