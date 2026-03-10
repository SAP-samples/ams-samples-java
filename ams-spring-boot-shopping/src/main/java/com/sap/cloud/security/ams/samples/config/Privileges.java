package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.ams.api.Privilege;

/**
 * Utility class defining static Privilege constants for the shopping application.
 * 
 * <p>These constants can be statically imported to refer to specific action:resource
 * tuples throughout the application. Each constant uses the AMS {@link Privilege}
 * class which provides the {@code toAuthority()} method for Spring Security integration.
 * 
 * <p>Usage examples:
 * <pre>{@code
 * // In SecurityConfiguration (with static import)
 * import static com.sap.cloud.security.ams.samples.config.Privileges.*;
 * 
 * authz.requestMatchers(GET, "/products")
 *     .hasAuthority(READ_PRODUCTS.toAuthority()); // "read:products"
 * 
 * // In service classes (with static import)
 * AmsAuthorization.checkPrivilege(CREATE_ORDERS, 
 *     Map.of("product.category", category));
 * }</pre>
 */
public final class Privileges {
    
    /**
     * Privilege for reading products (action: read, resource: products)
     */
    public static final Privilege READ_PRODUCTS = Privilege.of("read", "products");
    
    /**
     * Privilege for reading orders (action: read, resource: orders)
     */
    public static final Privilege READ_ORDERS = Privilege.of("read", "orders");
    
    /**
     * Privilege for creating orders (action: create, resource: orders)
     */
    public static final Privilege CREATE_ORDERS = Privilege.of("create", "orders");
    
    /**
     * Privilege for deleting orders (action: delete, resource: orders)
     */
    public static final Privilege DELETE_ORDERS = Privilege.of("delete", "orders");
    
    /**
     * Private constructor to prevent instantiation of utility class
     */
    private Privileges() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}
