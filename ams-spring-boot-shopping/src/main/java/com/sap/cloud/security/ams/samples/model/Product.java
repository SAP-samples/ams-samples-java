package com.sap.cloud.security.ams.samples.model;

import com.fasterxml.jackson.annotation.*;

/**
 * Product model representing a shopping item
 */
public class Product {
    private final int id;
    private final String name;
    private final double price;
    private final String category;

    @JsonCreator
    public Product(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("price") double price,
            @JsonProperty("category") String category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", category='" + category + '\'' +
                '}';
    }
}
