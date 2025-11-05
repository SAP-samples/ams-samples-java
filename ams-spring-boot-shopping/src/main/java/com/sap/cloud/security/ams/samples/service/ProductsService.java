package com.sap.cloud.security.ams.samples.service;

import java.util.List;

import com.sap.cloud.security.ams.spring.authorization.annotations.CheckPrivilege;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sap.cloud.security.ams.samples.db.SimpleDatabase;
import com.sap.cloud.security.ams.samples.model.Product;

/**
 * Service for handling product-related operations
 */
@Service
public class ProductsService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductsService.class);

    private final SimpleDatabase database;

    @Autowired
    public ProductsService(SimpleDatabase database) {
        this.database = database;
    }

    /**
     * Get all products
     * 
     * <p>Authorization is handled at the route level in SecurityConfiguration.
     * No additional authorization checks needed here.
     */
    @CheckPrivilege(action="read", resource="products")
    public List<Product> getProducts() {
        LOG.debug("Retrieving all products");
        return database.getProducts();
    }

    @CheckPrivilege(action="read", resource="products")
    public Product getProductById(int productId) {
        LOG.debug("Retrieving product with ID: {}", productId);
        return database.getProductById(productId).orElse(null);
    }
}
