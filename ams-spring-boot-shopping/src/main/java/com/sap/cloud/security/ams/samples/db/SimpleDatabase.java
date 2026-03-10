package com.sap.cloud.security.ams.samples.db;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.*;
import org.springframework.stereotype.Component;

import com.sap.cloud.security.ams.samples.model.*;

/**
 * Simple in-memory database for products and orders
 */
@Component
public class SimpleDatabase {
    private static final Logger logger = LoggerFactory.getLogger(SimpleDatabase.class);

    private final List<Product> products;
    private final List<Order> orders;
    private final AtomicInteger nextOrderId;

    public SimpleDatabase() {
        this.products = new ArrayList<>();
        this.orders = new ArrayList<>();
        this.nextOrderId = new AtomicInteger(1);
        loadInitialData();
    }

    private void loadInitialData() {
        // Load products first
        List<Product> loadedProducts = DataLoader.loadProducts();
        this.products.addAll(loadedProducts);

        // Load orders (depends on products for total amount calculation)
        List<Order> loadedOrders = DataLoader.loadOrders(loadedProducts);
        this.orders.addAll(loadedOrders);

        // Set next order ID to be higher than existing ones
        int maxId = loadedOrders.stream()
                .mapToInt(Order::getId)
                .max()
                .orElse(0);
        this.nextOrderId.set(maxId + 1);

        logger.info("Database initialized with {} products and {} orders",
                products.size(), orders.size());
    }

    public List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public List<Order> getOrders() {
        return new ArrayList<>(orders);
    }

    public List<Order> getOrdersByCreator(String createdBy) {
        return new ArrayList<>(orders.stream()
                .filter(o -> o.getCreatedBy().equals(createdBy))
                .toList());
    }

    public Optional<Product> getProductById(int productId) {
        return products.stream()
                .filter(p -> p.getId() == productId)
                .findFirst();
    }

    public Optional<Order> getOrderById(int orderId) {
        return orders.stream()
                .filter(o -> o.getId() == orderId)
                .findFirst();
    }

    public Order addOrder(Order order) {
        Order newOrder = order.withId(nextOrderId.getAndIncrement());
        orders.add(newOrder);
        logger.info("Added new order: {}", newOrder);
        return newOrder;
    }

    public boolean deleteOrder(int orderId) {
        boolean removed = orders.removeIf(o -> o.getId() == orderId);
        if (removed) {
            logger.info("Deleted order with ID: {}", orderId);
        }
        return removed;
    }

    public void reset() {
        orders.clear();
        products.clear();
        nextOrderId.set(1);
        loadInitialData();
        logger.info("Database reset to initial state");
    }
}
