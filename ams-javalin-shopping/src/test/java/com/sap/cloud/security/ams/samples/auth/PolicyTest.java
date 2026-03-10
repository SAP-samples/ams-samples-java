package com.sap.cloud.security.ams.samples.auth;

import com.sap.cloud.security.ams.AmsTestExtension;
import com.sap.cloud.security.ams.TestAuthorizationsProvider;
import com.sap.cloud.security.ams.api.expression.AttributeName;
import com.sap.cloud.security.ams.api.Authorizations;
import com.sap.cloud.security.ams.api.DecisionResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static com.sap.cloud.security.ams.Assertions.assertDecision;
import static com.sap.cloud.security.ams.Assertions.assertGranted;
import static com.sap.cloud.security.ams.samples.auth.Role.*;

public class PolicyTest {
    @RegisterExtension
    static AmsTestExtension amsTest = AmsTestExtension.fromLocalDcn();
    static TestAuthorizationsProvider SHOPPING_POLICIES;
    static TestAuthorizationsProvider LOCAL_POLICIES;
    static TestAuthorizationsProvider INTERNAL_POLICIES;

    Authorizations cut;

    @BeforeAll
    static void beforeAll() {
        SHOPPING_POLICIES = amsTest.forPackage("shopping");
        LOCAL_POLICIES = amsTest.forPackage("local");
        INTERNAL_POLICIES = amsTest.forPackage("internal");
    }

    @Test
    void testReadProducts() {
        cut = SHOPPING_POLICIES.getAuthorizations("ReadProducts");
        assertGranted(() -> cut.checkPrivilege(READ_PRODUCTS.asPrivilege()));
    }

    @Test
    void testDeleteOrders() {
        cut = SHOPPING_POLICIES.getAuthorizations("DeleteOrders");
        assertGranted(() -> cut.checkPrivilege(DELETE_ORDERS.asPrivilege()));
    }

    @Test
    void testCreateOrders() {
        cut = SHOPPING_POLICIES.getAuthorizations("CreateOrders");
        assertGranted(() -> cut.checkPrivilege(CREATE_ORDERS.asPrivilege()));
    }

    @Test
    void testReadOrders() {
        cut = SHOPPING_POLICIES.getAuthorizations("ReadOrders");
        assertGranted(() -> cut.checkPrivilege(READ_ORDERS.asPrivilege()));
    }

    @ParameterizedTest
    @CsvSource({
            "alice, alice, GRANTED",
            "alice, bob, DENIED"
    })
    void testReadOwnOrders(String userScimId, String orderCreatedBy, DecisionResult result) {
        cut = SHOPPING_POLICIES.getAuthorizations("ReadOwnOrders");
        assertDecision(result, () -> cut.checkPrivilege(READ_ORDERS.asPrivilege(),
                Map.of(
                        AttributeName.of("$user.scim_id"), userScimId,
                        AttributeName.of("order.createdBy"), orderCreatedBy
                )));
    }

    @ParameterizedTest
    @CsvSource({
            "accessory, 99, GRANTED",
            "accessory, 100, GRANTED",
            "periphery, 0, DENIED"
    })
    void testOrderAccesory(String productCategory, Double orderTotal, DecisionResult result) {
        cut = LOCAL_POLICIES.getAuthorizations("OrderAccessory");
        assertDecision(result, () -> cut.checkPrivilege(CREATE_ORDERS.asPrivilege(),
                Map.of(
                        AmsAttributes.PRODUCT_CATEGORY, productCategory,
                        AmsAttributes.ORDER_TOTAL, orderTotal
                )));
    }

    @ParameterizedTest
    @CsvSource({
            "accessory, 99, GRANTED",
            "accessory, 100, DENIED",
            "periphery, 0, GRANTED"
    })
    void testExternalOrder(String productCategory, Double orderTotal, DecisionResult result) {
        cut = INTERNAL_POLICIES.getAuthorizations("ExternalOrder");
        assertDecision(result, () -> cut.checkPrivilege(CREATE_ORDERS.asPrivilege(),
                Map.of(
                        AmsAttributes.PRODUCT_CATEGORY, productCategory,
                        AmsAttributes.ORDER_TOTAL, orderTotal
                )));
    }

    @ParameterizedTest
    @CsvSource({
            "accessory, 99, GRANTED",
            "accessory, 100, DENIED",
            "periphery, 0, DENIED"
    })
    void testFilteredOrder(String productCategory, Double orderTotal, DecisionResult result) {
        cut = LOCAL_POLICIES.getAuthorizations("OrderAccessory");
        cut.setLimit(INTERNAL_POLICIES.getAuthorizations("ExternalOrder"));

        assertDecision(result, () -> cut.checkPrivilege(CREATE_ORDERS.asPrivilege(),
                Map.of(
                        AmsAttributes.PRODUCT_CATEGORY, productCategory,
                        AmsAttributes.ORDER_TOTAL, orderTotal
                )));
    }
}