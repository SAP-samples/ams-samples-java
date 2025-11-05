package com.sap.cloud.security.ams.samples.service;

import static com.sap.cloud.security.ams.samples.config.AmsAttributes.ORDER_CREATED_BY;
import static com.sap.cloud.security.ams.samples.config.Privileges.*;

import java.util.*;
import java.util.stream.Collectors;

import com.sap.cloud.security.ams.samples.config.AmsAttributes;
import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.*;
import com.sap.cloud.security.ams.spring.authorization.annotations.AmsAttribute;
import com.sap.cloud.security.ams.spring.authorization.annotations.CheckPrivilege;
import com.sap.cloud.security.ams.spring.authorization.annotations.PrecheckPrivilege;
import com.sap.cloud.security.ams.api.*;
import com.sap.cloud.security.ams.dcn.visitor.SqlExtractor;
import com.sap.cloud.security.ams.dcn.visitor.SqlExtractor.SqlResult;
import com.sap.cloud.security.token.*;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for handling order-related operations with AMS authorization
 */
@Service
public class OrdersService {
    private static final Logger LOG = LoggerFactory.getLogger(OrdersService.class);

    private final SimpleDatabase database;
    private final Authorizations authorizations;
    private SqlExtractor extractGetOrdersSql;

    @Autowired
    public OrdersService(SimpleDatabase database, Authorizations authorizations) {
        this.database = database;
        this.authorizations = authorizations;
        this.initSqlExtractors();
    }

    private void initSqlExtractors() {
        Map<AttributeName, String> attributeMapping = Map.of(
                AttributeName.ofSegments("order", "createdBy"), "createdBy");
        extractGetOrdersSql = new SqlExtractor(attributeMapping);
    }

    /**
     * Delete an order
     * 
     * <p>
     * Uses simple authorization check without contextual attributes.
     */
    @CheckPrivilege(action="delete", resource="orders")
    public void deleteOrder(int orderId) {
        /*
         * --- SHOWCASES CONTEXT-FREE AMS AUTHORIZATION CHECK ---
         *
         * The following privilege check is equivalent to the route-level access check
         * configured in SecurityConfiguration using the AmsAuthorizationManager.
         * Doing the check here or via route-level access checks is a matter of
         * preference.
         *
         * This check ensures that the service logic executes only if the authorization
         * is unconditionally granted. It leads to an early exit (AccessDeniedException)
         * if the authorization is denied or conditional.
         *
         * Example (uncomment to perform the explicit pre-check inside the service):
         * if (!authorizations.checkPrivilege(DELETE_ORDERS).isGranted()) {
         * LOG.warn("Authorization denied for deleting order {}", orderId);
         * throw new AccessDeniedException();
         * }
         *
         * Use this pattern when the operation does not depend on entity-specific
         * attributes.
         */

        // Check if order exists
        database.getOrderById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Delete the order
        boolean deleted = database.deleteOrder(orderId);
        if (deleted) {
            String currentUser = SecurityContext.getToken().getClaimAsString(TokenClaims.SAP_GLOBAL_SCIM_ID);
            LOG.info("Deleted order {} by user: {}", orderId, currentUser);
        }
    }

    /**
     * Performs an order creation, secured with instance-based authorization.
     *
     * @param product the product
     * @param quantity the quantity
     * @param totalAmount the calculated total amount (used for authorization)
     * @param productCategory the product category (used for authorization)
     * @return the created order
     */
    @CheckPrivilege(action="create", resource="orders")
    public Order createOrder(
            Product product,
            int quantity,
            @AmsAttribute(name="order.total") double totalAmount,
            @AmsAttribute(name="product.category") String productCategory) {
        if(!Objects.equals(product.getCategory(), productCategory)) {
            throw new IllegalArgumentException("Authorization attribute for product category does not match the product");
        }

        if (Math.abs(product.getPrice() * quantity - totalAmount) > 0.01) {
            throw new IllegalArgumentException("Authorization attribute for order total does not match the computed total");
        }

        String userId = SecurityContext.getToken().getClaimAsString(TokenClaims.SAP_GLOBAL_SCIM_ID);
        Order newOrder = new Order(product.getId(), quantity, totalAmount, userId);
        Order savedOrder = database.addOrder(newOrder);

        LOG.info("Created new order {} for user: {} (category: {}, total: {})",
                savedOrder.getId(), userId, productCategory, totalAmount);
        return savedOrder;
    }

    /**
     * Get all orders with conditional filtering based on AMS policies
     * 
     * <p>
     * This method handles three authorization scenarios:
     * <ul>
     * <li>Denied - throws AccessDeniedException</li>
     * <li>Granted - returns all orders</li>
     * <li>Conditional - filters orders based on policy conditions (e.g., user can
     * only see their own orders)</li>
     * </ul>
     */
    @PrecheckPrivilege(action="read", resource="orders")
    public List<Order> getOrders() {
        /*
         * --- SHOWCASES CONTEXTUAL AMS AUTHORIZATION CHECK FOR A SET OF ENTITIES
         * (FILTER) ---
         * /*
         * The following privilege check is too complex to be done via route-level
         * access checks.
         * It distinguishes three cases based on the Decision result:
         *
         * 1) Unconditionally denied -> throw AccessDeniedException.
         * 2) Unconditionally granted -> return all resources.
         * 3) Conditional -> convert the condition into a DB filter or evaluate
         * per-entity.
         * *
         * For conditional cases consider converting the Decision to an SQL prepared
         * statement fragment
         * (recommended) or perform loop-based per-entity checks as shown below for
         * small datasets.
         */

        Decision decision = authorizations.checkPrivilege(READ_ORDERS);

        List<Order> orders;
        if (decision.isDenied()) {
            LOG.warn("Authorization denied for reading orders");
            throw new AccessDeniedException(null);
        } else if (decision.isGranted()) {
            // Unconditional access - return all orders
            orders = database.getOrders();
            LOG.info("Returned {} orders (unconditional access)", orders.size());
        } else {
            // Conditional access - filter based on policy conditions
            LOG.debug("Conditional authorization - filtering orders based on policy conditions");

            // Showcases SQL extraction for database-level filtering (recommended)
            SqlResult sqlResult = decision.visit(extractGetOrdersSql);
            LOG.info("SQL Filter for conditional access: {} with parameters {}", sqlResult.getSqlTemplate(),
                    sqlResult.getParameters());

            // Alternative loop-based filtering by checking each order individually (for small resources sets only)
            orders = database.getOrders().stream()
                    .filter(order -> authorizations
                            .checkPrivilege(READ_ORDERS, Map.of(ORDER_CREATED_BY, order.getCreatedBy())).isGranted())
                    .collect(Collectors.toList());
            LOG.info("Returned {} filtered orders (conditional access)", orders.size());
        }

        return orders;
    }
}
