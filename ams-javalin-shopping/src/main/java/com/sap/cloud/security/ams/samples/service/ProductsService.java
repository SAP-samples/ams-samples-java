package com.sap.cloud.security.ams.samples.service;

import java.util.List;

import com.sap.cloud.security.ams.samples.auth.AuthHandler;
import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.Product;
import com.sap.cloud.security.token.*;

import org.slf4j.*;

import io.javalin.http.*;

/**
 * Service for handling product-related operations
 */
public class ProductsService {
    private static final Logger logger = LoggerFactory.getLogger(ProductsService.class);

    private final SimpleDatabase database;
    private AuthHandler authHandler;

    public ProductsService(SimpleDatabase database, AuthHandler authHandler) {
        this.database = database;
        this.authHandler = authHandler;
    }

    public Handler getProducts() {
        return ctx -> {
            logger.debug("Processing GET /products request");

            try {
                List<Product> products = database.getProducts();
                ctx.json(products);
                logger.info("Returned {} products to user: {}",
                        products.size(), SecurityContext.getToken().getClaimAsString(TokenClaims.EMAIL));
            } catch (Exception e) {
                logger.error("Error retrieving products", e);
                throw new InternalServerErrorResponse();
            }
        };
    }
}
