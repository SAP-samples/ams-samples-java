package com.sap.cloud.security.ams.samples.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cloud.security.ams.api.AttributeName;
import com.sap.cloud.security.ams.api.Authorizations;
import com.sap.cloud.security.ams.api.Decision;
import com.sap.cloud.security.ams.dcn.visitor.SqlExtractor;
import com.sap.cloud.security.ams.samples.auth.AmsAttributes;
import com.sap.cloud.security.ams.samples.auth.AuthHandler;
import com.sap.cloud.security.ams.samples.auth.Role;
import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.Order;
import com.sap.cloud.security.ams.samples.model.Product;
import com.sap.cloud.security.token.SecurityContext;
import com.sap.cloud.security.token.TokenClaims;
import io.javalin.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for handling order-related operations
 */
public class OrdersService {
    private static final Logger logger = LoggerFactory.getLogger(OrdersService.class);

    private final SimpleDatabase database;
    private AuthHandler authHandler;
    private final ObjectMapper objectMapper;

    public OrdersService(SimpleDatabase database, AuthHandler authHandler) {
        this.database = database;
        this.authHandler = authHandler;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * DELETE /orders/:id - Delete an order
     */
    public Handler deleteOrder() {
        return ctx -> {
            /* --- SHOWCASES CONTEXT-FREE AMS AUTHORIZATION CHECK ---
            /*
             * The following privilege check is equivalent to the privilege check already
             * done via the AuthHandler for DELETE /orders/:id.
             * Doing the check here or via the AuthHandler is a matter of preference.
             * 
             * This check ensures that the service logic executes only if the authorization
             * is unconditionally granted.
             * It leads to an early exit with status code 403 if the authorization is denied
             * or depends on a condition.
             * 
             * Authorizations authorizations = authHandler.getAuthorizations();
             * if (!authorizations.checkPrivilege("delete", "orders").isGranted()) {
             * throw new ForbiddenResponse();
             * }
             * 
             * This pattern should be used when the authorization is not expected to depend
             * on any condition.
             */

            logger.debug("Processing DELETE /orders/{} request", ctx.pathParam("id"));

            try {
                int orderId = Integer.parseInt(ctx.pathParam("id"));

                // Check if order exists
                Optional<Order> orderOpt = database.getOrderById(orderId);
                if (orderOpt.isEmpty()) {
                    throw new NotFoundResponse("Order not found");
                }

                // Delete the order
                boolean deleted = database.deleteOrder(orderId);
                if (deleted) {
                    ctx.status(204);
                    logger.info("Deleted order {} by user: {}", orderId,
                            SecurityContext.getToken().getClaimAsString(TokenClaims.SAP_GLOBAL_SCIM_ID));
                } else {
                    throw new InternalServerErrorResponse();
                }
            } catch (NumberFormatException e) {
                throw new BadRequestResponse("Invalid order ID");
            } catch (Exception e) {
                logger.error("Error deleting order", e);
                throw new InternalServerErrorResponse();
            }
        };
    }

    /**
     * POST /orders - Create a new order
     */
    public Handler createOrder() {
        return ctx -> {
            // --- SHOWCASES CONTEXTUAL AMS AUTHORIZATION CHECK FOR A SINGLE ENTITY ---

            /*
             * --- PRE-CHECK ---
             * The following privilege check is equivalent to the *pre*-check already done
             * via the AuthHandler for POST /orders.
             * Doing the check here or via the AuthHandler is a matter of preference.
             * 
             * This check ensures that the service logic executes only if the authorization
             * is granted or depends on a condition.
             * It leads to an early exit with status code 403 if the authorization is
             * unconditionally denied.
             * 
             * Authorizations authorizations = authHandler.getAuthorizations();
             * if (authorizations.checkPrivilege("create", "orders").isDenied()) {
             * throw new ForbiddenResponse();
             * }
             * 
             * This pattern should be used when the authorization is expected to depend on a
             * condition.
             */

            logger.debug("Processing POST /orders request");

            JsonNode requestBody = objectMapper.readTree(ctx.body());

            // Validate request body
            if (!requestBody.has("productId") || !requestBody.has("quantity")) {
                throw new BadRequestResponse("Invalid productId or quantity");
            }

            int productId = requestBody.get("productId").asInt();
            int quantity = requestBody.get("quantity").asInt();

            if (productId < 0 || quantity <= 0) {
                throw new BadRequestResponse("Invalid productId or quantity");
            }

            // Find the product
            Optional<Product> productOpt = database.getProductById(productId);
            if (productOpt.isEmpty()) {
                throw new NotFoundResponse("Product not found");
            }

            Product product = productOpt.get();
            double totalAmount = product.getPrice() * quantity;

            // --- ENTITY-SPECIFIC PRIVILEGE CHECK IN HANDLER ---
            Authorizations authorizations = authHandler.getAuthorizations();
            Decision decision = authorizations.checkPrivilege(
                    Role.CREATE_ORDERS.getAction(),
                    Role.CREATE_ORDERS.getResource(),
                    Map.of(AmsAttributes.PRODUCT_CATEGORY, product.getCategory(),
                            AmsAttributes.ORDER_TOTAL, totalAmount));
            if (!decision.isGranted()) {
                throw new ForbiddenResponse();
            }

            String currentUser = SecurityContext.getToken().getClaimAsString(TokenClaims.SAP_GLOBAL_SCIM_ID);

            // Create and save the order
            Order newOrder = new Order(productId, quantity, totalAmount, currentUser);
            Order savedOrder = database.addOrder(newOrder);

            ctx.status(201).json(savedOrder);
            logger.info("Created new order {} for user: {}", savedOrder.getId(), currentUser);

        };
    }

    /**
     * GET /orders - Get all orders (with filtering based on authorization)
     */
    public Handler getOrders() {
        return ctx -> {
            // --- SHOWCASES CONTEXTUAL AMS AUTHORIZATION CHECK FOR A SET OF ENTITIES
            // (FILTER) ---

            /*
             * --- COMPLEX PRIVILEGE CHECK ---
             * The following privilege check is too complex to be done via the AuthHandler
             * for GET /orders.
             * It distinguishes between three different cases:
             * 1. The authorization is unconditionally denied.
             * 2. The authorization is unconditionally granted.
             * 3. The authorization depends on a condition.
             * 
             * This pattern should be used when the authorization is expected to potentially
             * (but not necessarily) depend on a condition.
             */

            logger.debug("Processing GET /orders request");

            Authorizations authorizations = authHandler.getAuthorizations();
            Decision decision = authorizations.checkPrivilege(
                    Role.READ_ORDERS.getAction(),
                    Role.READ_ORDERS.getResource());

            List<Order> orders;
            if (decision.isDenied()) {
                // --- REDUNDANT PRIVILEGE PRE-CHECK IN HANDLER TO SHOWCASE API (ALREADY DONE
                // VIA AUTHHANDLER) ---
                throw new ForbiddenResponse();
            } else if (decision.isGranted()) {
                orders = database.getOrders();
            } else {
                /*
                 * --- CONVERT AMS CONDITION TO DATABASE CONDITION ---
                 * The following code demonstrates how Decision#visit can be used to convert
                 * from a DCN condition to a database condition.
                 * In production, the result will typically be an SQL 'where condition template'
                 * with parameters for use in an SQL prepared statement.
                 * In this sample though, the data lies in Java collections instead of a
                 * database, so we demonstrate two approaches:
                 * 
                 * 1. SqlExtractor: Transforms DCN to SQL condition template
                 * - Maps AMS attribute names to database field references, e.g.
                 * "$app.order.createdBy" to "createdBy"
                 * - Generates parameterized SQL WHERE clause templates for use with prepared
                 * statements
                 * 
                 * 2. Loop-based filtering: For small resource sets, evaluates the condition for
                 * each entity individually.
                 * - Checks authorization per entity using the actual entity's attribute values
                 * - Filters the collection based on authorization results
                 */

                // Alternative 1: Showcases SQL condition generation (for use with databases)
                SqlExtractor.SqlResult sqlCondition = decision.visit(new SqlExtractor(Map.of(
                        AttributeName.of("order.createdBy"), "createdBy")));
                logger.info("Generated SQL WHERE condition template: " + sqlCondition.getSqlTemplate() +
                        " with parameters " + sqlCondition.getParameters());

                // Alternative 2: Showcases loop-based authorization check (for small resource
                // sets)
                orders = database.getOrders().stream()
                        .filter(order -> authorizations.checkPrivilege(
                                Role.READ_ORDERS.getAction(),
                                Role.READ_ORDERS.getResource(),
                                Map.of(AmsAttributes.ORDER_CREATED_BY, order.getCreatedBy())).isGranted())
                        .collect(Collectors.toList());
            }

            ctx.json(orders);
            logger.info("Returned {} orders to user: {}",
                    orders.size(), SecurityContext.getToken().getClaimAsString(TokenClaims.EMAIL));
        };
    }
}
