package com.sap.cloud.security.ams.samples;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cloud.security.ams.samples.auth.TestAuthHandler;
import com.sap.cloud.security.ams.samples.model.Order;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static java.net.http.HttpRequest.BodyPublishers.noBody;
import static org.junit.jupiter.api.Assertions.*;

public class JavalinShoppingApplicationTest {
    private static TestAuthHandler testAuthHandler;
    private static ObjectMapper objectMapper;

    // JWT constants - loaded once at test class initialization
    private static String ALICE_JWT;
    private static String BOB_JWT;
    private static String CAROL_JWT;
    private static String BOB_EXTERNAL_ORDER_JWT;

    private Javalin app;

    @BeforeAll
    public static void beforeAll() throws IOException {
        testAuthHandler = new TestAuthHandler();
        objectMapper = new ObjectMapper();

        // Load JWTs once during class initialization
        ALICE_JWT = loadJwtFromFile("User_alice.json");
        BOB_JWT = loadJwtFromFile("User_bob.json");
        CAROL_JWT = loadJwtFromFile("User_carol.json");
        BOB_EXTERNAL_ORDER_JWT = loadJwtFromFile("RestrictedPrincipalPropagation_bob.json");
    }

    @BeforeEach
    public void setUp() {
        app = AppFactory.createApp(testAuthHandler);
    }

    @Test
    public void testHealthEndpoint() {
        JavalinTest.test(app, (server, client) -> {
            // Test health endpoint (should be accessible without auth)
            var response = client.get("/health");
            assertEquals(200, response.code());
        });
    }

    // GET /products tests
    @Test
    public void testProductsAllowedForUserWithReadProductsPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/products", req -> {
                req.header("Authorization", "Bearer " + ALICE_JWT);
            });
            assertEquals(200, response.code());
        });
    }

    @Test
    public void testProductsDeniedWithoutReadProductsPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/products", req -> {
                req.header("Authorization", "Bearer " + CAROL_JWT);
            });
            assertEquals(403, response.code());
        });
    }

    // DELETE /orders/:id tests
    @Test
    public void testDeleteOrdersAllowedWithDeleteOrdersPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.request("/orders/1", req -> {
                req.header("Authorization", "Bearer " + ALICE_JWT);
                req.delete(noBody());
            });
            assertEquals(204, response.code());
        });
    }

    @Test
    public void testDeleteOrdersDeniedForOtherUsersWithoutDeleteOwnOrdersPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.request("/orders/4", req -> {
                req.header("Authorization", "Bearer " + BOB_JWT);
                req.delete(noBody());
            });
            assertEquals(403, response.code());
        });
    }

    // POST /orders tests
    @Test
    public void testCreateOrderAllowedForUserWithCreateOrdersPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/orders", "{\"productId\": 1, \"quantity\": 1000}",
                    req -> {
                        req.header("Authorization", "Bearer " + ALICE_JWT);
                    });
            assertEquals(201, response.code());
        });
    }

    @Test
    public void testCreateOrderOnlyAllowedForAccessoriesWithOrderAccessoryPolicy() {
        JavalinTest.test(app, (server, client) -> {
            // Test ordering non-accessory item (should be denied)
            var response1 = client.post("/orders", "{\"productId\": 5, \"quantity\": 1}",
                    req -> {
                        req.header("Authorization", "Bearer " + BOB_JWT);
                    });
            assertEquals(403, response1.code());

            // Test ordering accessory item (should be allowed)
            var response2 = client.post("/orders", "{\"productId\": 4, \"quantity\": 1}",
                    req -> {
                        req.header("Authorization", "Bearer " + BOB_JWT);
                    });
            assertEquals(201, response2.code());
        });
    }

    @Test
    public void testCreateOrderExternalOrderContextWithVolumeRestrictions() {
        JavalinTest.test(app, (server, client) -> {
            // Test order too expensive (160 > 100)
            var response1 = client.post("/orders", "{\"productId\": 4, \"quantity\": 4}",
                    req -> {
                        req.header("Authorization", "Bearer " + BOB_EXTERNAL_ORDER_JWT);
                    });
            assertEquals(403, response1.code());

            // Test wrong product category (securityAccessory instead of accessory)
            var response2 = client.post("/orders", "{\"productId\": 5, \"quantity\": 1}",
                    req -> {
                        req.header("Authorization", "Bearer " + BOB_EXTERNAL_ORDER_JWT);
                    });
            assertEquals(403, response2.code());

            // Test order total too expensive (120)
            var response3 = client.post("/orders", "{\"productId\": 5, \"quantity\": 4}",
                    req -> {
                        req.header("Authorization", "Bearer " + BOB_EXTERNAL_ORDER_JWT);
                    });
            assertEquals(403, response3.code());

            // Test valid order (should be allowed)
            var response4 = client.post("/orders", "{\"productId\": 4, \"quantity\": 2}",
                    req -> {
                        req.header("Authorization", "Bearer " + BOB_EXTERNAL_ORDER_JWT);
                    });
            assertEquals(201, response4.code());
        });
    }

    // GET /orders tests
    @Test
    public void testGetOrdersAllowedForUserWithReadOrdersPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/orders", req -> {
                req.header("Authorization", "Bearer " + ALICE_JWT);
            });
            assertEquals(200, response.code());

            try {
                String responseBody = response.body().string();
                List<Order> orders = objectMapper.readValue(responseBody, new TypeReference<>() {
                });

                assertNotNull(orders);
                assertEquals(4, orders.size());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGetOrdersFilteredForUserWithReadOwnOrdersPolicy() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/orders", req -> {
                req.header("Authorization", "Bearer " + BOB_JWT);
            });
            assertEquals(200, response.code());

            try {
                // Decode response into List<Order>
                String responseBody = response.body().string();
                List<Order> orders = objectMapper.readValue(responseBody,
                        new TypeReference<>() {
                        });

                assertNotNull(orders);
                assertFalse(orders.isEmpty());
                assertTrue(orders.stream().allMatch(o -> "bob".equals(o.getCreatedBy())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGetOrdersDeniedWithoutReadOrdersPrivilege() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/orders", req -> {
                req.header("Authorization", "Bearer " + CAROL_JWT);
            });
            assertEquals(403, response.code());
        });
    }

    // JWT Helper Methods
    private static String loadJwtFromFile(String filename) throws IOException {
        Path filePath = Path.of("src/test/resources/jwt", filename);
        String jsonPayload = Files.readString(filePath);
        return createTestJwt(jsonPayload);
    }

    private static String createTestJwt(String jsonPayload) {
        String header = Base64.getEncoder().encodeToString("{}".getBytes());
        String payload = Base64.getEncoder().encodeToString(jsonPayload.getBytes());
        String signature = "signature";
        return header + "." + payload + "." + signature;
    }
}