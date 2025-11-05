package com.sap.cloud.security.ams.samples.model;

import com.fasterxml.jackson.annotation.*;

/**
 * Order model representing a customer order
 */
public class Order {
    private final int id;
    private final int productId;
    private final int quantity;
    private final double totalAmount;
    private final String createdBy;

    @JsonCreator
    public Order(
            @JsonProperty("id") int id,
            @JsonProperty("productId") int productId,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("totalAmount") double totalAmount,
            @JsonProperty("createdBy") String createdBy) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.createdBy = createdBy;
    }

    // Constructor for creating new orders (without ID)
    public Order(int productId, int quantity, double totalAmount, String createdBy) {
        this(0, productId, quantity, totalAmount, createdBy);
    }

    // Constructor for creating orders with ID
    public Order withId(int newId) {
        return new Order(newId, this.productId, this.quantity, this.totalAmount, this.createdBy);
    }

    public int getId() {
        return id;
    }

    public int getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", totalAmount=" + totalAmount +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }
}
