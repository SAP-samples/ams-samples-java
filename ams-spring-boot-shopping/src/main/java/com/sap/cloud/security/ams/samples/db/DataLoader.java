package com.sap.cloud.security.ams.samples.db;

import java.io.*;
import java.util.*;

import org.slf4j.*;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.sap.cloud.security.ams.samples.model.*;

/**
 * Utility class for loading initial data from CSV files
 */
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    /**
     * Load products from CSV file
     */
    public static List<Product> loadProducts() {
        List<Product> products = new ArrayList<>();

        try (InputStream is = DataLoader.class.getResourceAsStream("/csv/products.csv");
                CSVReader reader = new CSVReader(new InputStreamReader(is))) {

            List<String[]> records = reader.readAll();

            // Skip header row
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length >= 4) {
                    try {
                        int id = Integer.parseInt(record[0]);
                        String name = record[1].replace("\"", ""); // Remove quotes
                        double price = Double.parseDouble(record[2]);
                        String category = record[3];

                        products.add(new Product(id, name, price, category));
                    } catch (NumberFormatException e) {
                        logger.warn("Skipping invalid product record: {}", String.join(",", record));
                    }
                }
            }

            logger.info("Loaded {} products from CSV", products.size());

        } catch (IOException | CsvException e) {
            logger.error("Error loading products from CSV", e);
        }

        return products;
    }

    /**
     * Load orders from CSV file
     */
    public static List<Order> loadOrders(List<Product> products) {
        List<Order> orders = new ArrayList<>();

        try (InputStream is = DataLoader.class.getResourceAsStream("/csv/orders.csv");
                CSVReader reader = new CSVReader(new InputStreamReader(is))) {

            List<String[]> records = reader.readAll();

            // Skip header row
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length >= 4) {
                    try {
                        int id = Integer.parseInt(record[0]);
                        int productId = Integer.parseInt(record[1]);
                        int quantity = Integer.parseInt(record[2]);
                        String createdBy = record[3];

                        // Find the product to calculate total amount
                        Product product = products.stream()
                                .filter(p -> p.getId() == productId)
                                .findFirst()
                                .orElse(null);

                        if (product != null) {
                            double totalAmount = product.getPrice() * quantity;
                            orders.add(new Order(id, productId, quantity, totalAmount, createdBy));
                        } else {
                            logger.warn("Product with ID {} not found for order {}", productId, id);
                        }

                    } catch (NumberFormatException e) {
                        logger.warn("Skipping invalid order record: {}", String.join(",", record));
                    }
                }
            }

            logger.info("Loaded {} orders from CSV", orders.size());

        } catch (IOException | CsvException e) {
            logger.error("Error loading orders from CSV", e);
        }

        return orders;
    }
}
