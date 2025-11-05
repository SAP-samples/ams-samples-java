package com.sap.cloud.security.ams.samples;

import org.slf4j.*;

import com.sap.cloud.security.ams.samples.auth.AuthHandler;

import io.javalin.Javalin;

/**
 * Main application class for the AMS Javalin Shopping sample
 */
public class JavalinShoppingApplication {
    private static final Logger logger = LoggerFactory.getLogger(JavalinShoppingApplication.class);

    private static final int DEFAULT_PORT = 7000;

    public static void main(String[] args) {
        logger.info("Starting AMS Javalin Shopping Application...");

        // Get port from environment or use default
        int port = getPort();

        try {
            AuthHandler authHandler = new AuthHandler();

            Javalin app = AppFactory.createApp(authHandler);

            // Start the server
            app.start(port);

            logger.info("AMS Javalin Shopping Application started successfully on port {}", port);
            logger.info("Health check available at: http://localhost:{}/health", port);

            // Graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down AMS Javalin Shopping Application...");
                app.stop();
                authHandler.getAmsClient().stop();
                logger.info("Application stopped successfully");
            }));

        } catch (Exception e) {
            logger.error("Failed to start AMS Javalin Shopping Application", e);
            System.exit(1);
        }
    }

    /**
     * Get the port from environment variables or system properties
     */
    private static int getPort() {
        // Try environment variable first (common in cloud deployments)
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                return Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable: {}, using default", portEnv);
            }
        }

        // Try system property
        String portProp = System.getProperty("server.port");
        if (portProp != null && !portProp.isEmpty()) {
            try {
                return Integer.parseInt(portProp);
            } catch (NumberFormatException e) {
                logger.warn("Invalid server.port system property: {}, using default", portProp);
            }
        }

        return DEFAULT_PORT;
    }
}
