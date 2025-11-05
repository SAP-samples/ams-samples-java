package com.sap.cloud.security.ams.samples.controller;

import java.util.List;
import java.util.Optional;

import com.sap.cloud.security.ams.samples.model.Product;
import com.sap.cloud.security.ams.samples.service.ProductsService;
import com.sap.cloud.security.ams.spring.authorization.annotations.PrecheckPrivilege;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sap.cloud.security.ams.samples.model.Order;
import com.sap.cloud.security.ams.samples.service.OrdersService;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for order operations
 * 
 * <p>This controller delegates all business logic and authorization checks
 * to the OrdersService. Spring Boot automatically translates AccessDeniedException
 * to 403 Forbidden responses.
 */
@RestController
@RequestMapping("/orders")
public class OrdersController {
    private static final Logger LOG = LoggerFactory.getLogger(OrdersController.class);

    private final ProductsService productsService;
    private final OrdersService ordersService;

    @Autowired
    public OrdersController(OrdersService ordersService, ProductsService productsService) {
        this.productsService = productsService;
        this.ordersService = ordersService;
    }

    /**
     * Get all orders (with filtering based on authorization)
     */
    @GetMapping
    public List<Order> getOrders() {
        return ordersService.getOrders();
    }

    /**
     * Create a new order
     */
    @PostMapping
    @PrecheckPrivilege(action = "create", resource = "orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        int productId = request.getProductId();
        int quantity = request.getQuantity();
        if (productId < 0 || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid productId or quantity");
        }

        // Fetch product (triggers read authorization check)
        Product product = productsService.getProductById(productId);

        if(product == null) {
            LOG.warn("Product with ID {} not found", productId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found");
        }

        // Compute authorization parameters
        double totalAmount = product.getPrice() * quantity;
        String productCategory = product.getCategory();

        // Call service with all needed context
        Order order = ordersService.createOrder(product, quantity, totalAmount, productCategory);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Delete an order
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable int id) {
        ordersService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Request DTO for creating orders
     */
    public static class CreateOrderRequest {
        private int productId;
        private int quantity;

        public int getProductId() {
            return productId;
        }

        public void setProductId(int productId) {
            this.productId = productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
