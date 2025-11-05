package com.sap.cloud.security.ams.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sap.cloud.security.ams.samples.config.TestSecurityConfiguration;
import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.Order;
import com.sap.cloud.security.ams.api.Privilege;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the Spring Boot Shopping application
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
class ApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleDatabase database;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Reset database to initial state before each test to ensure test independence
        database.reset();
        
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

    // Health endpoint test
    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // GET /privileges test
    @Test
    void testPrivilegesEndpoint() throws Exception {
        String aliceJwt = getAliceJwt();
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
        assertEquals(Set.of(
            Privilege.of("read", "products"),
            Privilege.of("create", "orders"), 
            Privilege.of("delete", "orders"),
            Privilege.of("read", "orders")
        ), privileges);
    }

    // GET /products tests
    @Test
    void testProductsAllowedForUserWithReadProductsPolicy() throws Exception {
        String aliceJwt = getAliceJwt();
        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isOk());
    }

    @Test
    void testProductsDeniedWithoutReadProductsPolicy() throws Exception {
        String carolJwt = getCarolJwt();
        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isForbidden());
    }

    // DELETE /orders/:id tests
    @Test
    void testDeleteOrdersAllowedWithDeleteOrdersPolicy() throws Exception {
        String aliceJwt = getAliceJwt();
        mockMvc.perform(delete("/orders/1")
                .header("Authorization", "Bearer " + aliceJwt))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteOrdersDeniedForOtherUsersWithoutDeleteOwnOrdersPolicy() throws Exception {
        String bobJwt = getBobJwt();
        mockMvc.perform(delete("/orders/4")
                .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isForbidden());
    }

    // POST /orders tests
    @Test
    void testCreateOrderAllowedForUserWithCreateOrdersPolicy() throws Exception {
        String aliceJwt = getAliceJwt();
        String requestBody = "{\"productId\": 1, \"quantity\": 1000}";

        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + aliceJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void testCreateOrderOnlyAllowedForAccessoriesWithOrderAccessoryPolicy() throws Exception {
        String bobJwt = getBobJwt();

        // Test ordering non-accessory item (should be denied)
        String request1 = "{\"productId\": 5, \"quantity\": 1}";
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request1))
                .andExpect(status().isForbidden());

        // Test ordering accessory item (should be allowed)
        String request2 = "{\"productId\": 4, \"quantity\": 1}";
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request2))
                .andExpect(status().isCreated());
    }

    @Test
    void testCreateOrderExternalOrderContextWithVolumeRestrictions() throws Exception {
        String bobExternalJwt = getBobExternalOrderJwt();

        // Test order too expensive (160 > 100)
        String request1 = "{\"productId\": 4, \"quantity\": 4}";
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobExternalJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request1))
                .andExpect(status().isForbidden());

        // Test wrong product category (securityAccessory instead of accessory)
        String request2 = "{\"productId\": 5, \"quantity\": 1}";
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobExternalJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request2))
                .andExpect(status().isForbidden());

        // Test valid order (should be allowed)
        String request3 = "{\"productId\": 4, \"quantity\": 2}";
        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + bobExternalJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request3))
                .andExpect(status().isCreated());
    }

    // GET /orders tests
    @Test
    void testGetOrdersAllowedForUserWithReadOrdersPolicy() throws Exception {
        String aliceJwt = getAliceJwt();
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
    void testGetOrdersFilteredForUserWithReadOwnOrdersPolicy() throws Exception {
        String bobJwt = getBobJwt();
        String response = mockMvc.perform(get("/orders")
                .header("Authorization", "Bearer " + bobJwt))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Order> orders = objectMapper.readValue(response, new TypeReference<List<Order>>() {
        });
        Assertions.assertNotNull(orders);
        Assertions.assertTrue(orders.size() > 0);
        Assertions.assertTrue(orders.stream().allMatch(o -> "bob".equals(o.getCreatedBy())));
    }

    @Test
    void testGetOrdersDeniedWithoutReadOrdersPrivilege() throws Exception {
        String carolJwt = getCarolJwt();
        mockMvc.perform(get("/orders")
                .header("Authorization", "Bearer " + carolJwt))
                .andExpect(status().isForbidden());
    }

    // JWT Helper Methods
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

    private String getAliceJwt() throws IOException {
        return loadJwtFromFile("User_alice.json");
    }

    private String getBobJwt() throws IOException {
        return loadJwtFromFile("User_bob.json");
    }

    private String getCarolJwt() throws IOException {
        return loadJwtFromFile("User_carol.json");
    }

    private String getBobExternalOrderJwt() throws IOException {
        return loadJwtFromFile("RestrictedPrincipalPropagation_bob.json");
    }
}
