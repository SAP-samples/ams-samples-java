package com.sap.cloud.security.ams.samples;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sap.cloud.security.ams.samples.auth.*;
import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.HealthStatus;
import com.sap.cloud.security.ams.samples.service.*;

import org.slf4j.*;

import io.javalin.Javalin;

/**
 * Factory class for creating and configuring the Javalin application.
 * Handles dependency injection of the AuthHandler as there is no DI framework
 * used.
 */
public class AppFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AppFactory.class);
    private static final AtomicBoolean isReady = new AtomicBoolean(false);

    /**
     * Create and configure the Javalin application
     * 
     * @param authHandler The authentication handler to use
     * @return Configured Javalin application
     */
    public static Javalin createApp(AuthHandler authHandler) {
        LOG.info("Creating Javalin application...");

        SimpleDatabase database = new SimpleDatabase();

        ProductsService productsService = new ProductsService(database, authHandler);
        OrdersService ordersService = new OrdersService(database, authHandler);
        PrivilegesService privilegesService = new PrivilegesService(authHandler);

        Javalin app = Javalin.create();

        app.beforeMatched(authHandler::handle);
        app.after(authHandler::clear);

        app.get("/health", ctx -> {
            if (isReady.get()) {
                ctx.json(HealthStatus.up());
            } else {
                ctx.status(503).json(HealthStatus.down("Service is not ready"));
            }
        });

        // API endpoints
        app.get("/privileges", privilegesService.getPrivileges(), Role.AUTHENTICATED);
        app.get("/products", productsService.getProducts(), Role.READ_PRODUCTS);
        app.get("/orders", ordersService.getOrders(), Role.READ_ORDERS);
        app.post("/orders", ordersService.createOrder(), Role.CREATE_ORDERS);
        app.delete("/orders/{id}", ordersService.deleteOrder(), Role.DELETE_ORDERS);

        // Global error handler
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled exception", e);
            ctx.status(500).result("Internal server error");
        });

        // Wait up to 30s for AMS to become ready
        authHandler.getAmsClient().whenReady().orTimeout(30, TimeUnit.SECONDS).thenRun(() -> {
            isReady.set(true);
            LOG.info("AMS is ready, application is now ready to serve requests");
        }).exceptionally(ex -> {
            LOG.error("AMS failed to become ready within the timeout", ex);
            System.exit(1);
            return null;
        });

        return app;
    }
}
