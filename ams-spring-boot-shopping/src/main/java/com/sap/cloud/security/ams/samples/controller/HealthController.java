package com.sap.cloud.security.ams.samples.controller;

import org.springframework.web.bind.annotation.*;

import com.sap.cloud.security.ams.samples.model.HealthStatus;

/**
 * Controller for health check endpoint
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthStatus health() {
        return HealthStatus.up();
    }
}
