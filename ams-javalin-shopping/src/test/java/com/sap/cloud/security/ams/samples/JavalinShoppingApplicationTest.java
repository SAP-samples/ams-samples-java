package com.sap.cloud.security.ams.samples;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cloud.security.ams.samples.auth.TestAuthHandler;
import com.sap.cloud.security.ams.samples.model.Order;

import io.javalin.Javalin;
import io.javalin.testtools.*;
import okhttp3.*;

public class JavalinShoppingApplicationTest {
    private static TestAuthHandler testAuthHandler;
    private static ObjectMapper objectMapper;

    private Javalin app;
    private static TestConfig testConfig;

    @BeforeAll
    public static void beforeAll() {
        testAuthHandler = new TestAuthHandler();
        objectMapper = new ObjectMapper();
        OkHttpClient client = (new OkHttpClient.Builder())
                // for debugging requests to prevent process shutdown after request timeout
                .connectTimeout(99999, TimeUnit.SECONDS)
                .readTimeout(9999, TimeUnit.SECONDS)
                .writeTimeout(99999, TimeUnit.SECONDS)
                .build();
        testConfig = new TestConfig(false, false, client);
    }

    @BeforeEach
    public void setUp() {
        app = AppFactory.createApp(testAuthHandler);
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            System.out.println("Exception occurred: " + e.getMessage());
        });
    }

    @Test
    public void testHealthEndpoint() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            // Test health endpoint (should be accessible without auth)
            var response = client.get("/health");
            assertEquals(200, response.code());
        });
    }

    // GET /products tests
    @Test
    public void testProductsAllowedForUserWithReadProductsPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String aliceJwt = getAliceJwt();
                var response = client.get("/products", (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + aliceJwt);
                });
                assertEquals(200, response.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testProductsDeniedWithoutReadProductsPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String carolJwt = getCarolJwt();
                var response = client.get("/products", (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + carolJwt);
                });
                assertEquals(403, response.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // DELETE /orders/:id tests
    @Test
    public void testDeleteOrdersAllowedWithDeleteOrdersPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String aliceJwt = getAliceJwt();
                var response = client.delete("/orders/1", null, (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + aliceJwt);
                });
                assertEquals(204, response.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testDeleteOrdersDeniedForOtherUsersWithoutDeleteOwnOrdersPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String bobJwt = getBobJwt();
                var response = client.delete("/orders/4", null, (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + bobJwt);
                });
                assertEquals(403, response.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // POST /orders tests
    @Test
    public void testCreateOrderAllowedForUserWithCreateOrdersPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String aliceJwt = getAliceJwt();
                var response = client.post("/orders", "{\"productId\": 1, \"quantity\": 1000}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + aliceJwt);
                        });
                assertEquals(201, response.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testCreateOrderOnlyAllowedForAccessoriesWithOrderAccessoryPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String bobJwt = getBobJwt();
                // Test ordering non-accessory item (should be denied)
                var response1 = client.post("/orders", "{\"productId\": 5, \"quantity\": 1}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + bobJwt);
                        });
                assertEquals(403, response1.code());

                // Test ordering accessory item (should be allowed)
                var response2 = client.post("/orders", "{\"productId\": 4, \"quantity\": 1}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + bobJwt);
                        });
                assertEquals(201, response2.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testCreateOrderExternalOrderContextWithVolumeRestrictions() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String bobExternalJwt = getBobExternalOrderJwt();
                // Test order too expensive (160 > 100)
                var response1 = client.post("/orders", "{\"productId\": 4, \"quantity\": 4}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + bobExternalJwt);
                        });
                assertEquals(403, response1.code());

                // Test wrong product category (securityAccessory instead of accessory)
                var response2 = client.post("/orders", "{\"productId\": 5, \"quantity\": 1}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + bobExternalJwt);
                        });
                assertEquals(403, response2.code());

                // Test order total too expensive (120)
                var response3 = client.post("/orders", "{\"productId\": 5, \"quantity\": 4}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + bobExternalJwt);
                        });
                assertEquals(403, response3.code());

                // Test valid order (should be allowed)
                var response4 = client.post("/orders", "{\"productId\": 4, \"quantity\": 2}",
                        (Request.Builder requestBuilder) -> {
                            requestBuilder.header("Authorization", "Bearer " + bobExternalJwt);
                        });
                assertEquals(201, response4.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // GET /orders tests
    @Test
    public void testGetOrdersAllowedForUserWithReadOrdersPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String aliceJwt = getAliceJwt();
                var response = client.get("/orders", (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + aliceJwt);
                });
                assertEquals(200, response.code());

                String responseBody = response.body().string();
                List<Order> orders = objectMapper.readValue(responseBody, new TypeReference<List<Order>>() {
                });

                assertTrue(orders != null);
                assertEquals(4, orders.size());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGetOrdersFilteredForUserWithReadOwnOrdersPolicy() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String bobJwt = getBobJwt();
                var response = client.get("/orders", (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + bobJwt);
                });
                assertEquals(200, response.code());

                // Decode response into List<Order>
                String responseBody = response.body().string();
                List<Order> orders = objectMapper.readValue(responseBody,
                        new TypeReference<List<Order>>() {
                        });

                assertTrue(orders != null);
                assertTrue(orders.size() > 0);
                assertTrue(orders.stream().allMatch(o -> "bob".equals(o.getCreatedBy())));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGetOrdersDeniedWithoutReadOrdersPrivilege() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            try {
                String carolJwt = getCarolJwt();
                var response = client.get("/orders", (Request.Builder requestBuilder) -> {
                    requestBuilder.header("Authorization", "Bearer " + carolJwt);
                });
                assertEquals(403, response.code());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // JWT Helper Methods
    private String loadJwtFromFile(String filename) throws IOException {
        Path filePath = Path.of("src/test/resources/jwt", filename);
        String jsonPayload = Files.readString(filePath);
        return createTestJwt(jsonPayload);
    }

    private String createTestJwt(String jsonPayload) {
        String header = Base64.getEncoder().encodeToString("{}".getBytes());
        String payload = Base64.getEncoder().encodeToString(jsonPayload.getBytes());
        String signature = "signature";
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
