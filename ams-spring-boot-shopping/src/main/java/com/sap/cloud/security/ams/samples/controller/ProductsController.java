package com.sap.cloud.security.ams.samples.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.sap.cloud.security.ams.samples.model.Product;
import com.sap.cloud.security.ams.samples.service.ProductsService;

/**
 * REST controller for product operations
 */
@RestController
@RequestMapping("/products")
public class ProductsController {

    private final ProductsService productsService;

    @Autowired
    public ProductsController(ProductsService productsService) {
        this.productsService = productsService;
    }

    @GetMapping
    public List<Product> getProducts() {
        return productsService.getProducts();
    }
}
