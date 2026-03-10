package com.sap.cloud.security.ams.samples.controller;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.sap.cloud.security.ams.samples.service.PrivilegesService;
import com.sap.cloud.security.ams.api.Privilege;

/**
 * REST controller for privileges information
 */
@RestController
@RequestMapping("/privileges")
public class PrivilegesController {

    private final PrivilegesService privilegesService;

    @Autowired
    public PrivilegesController(PrivilegesService privilegesService) {
        this.privilegesService = privilegesService;
    }

    @GetMapping
    public Set<Privilege> getPrivileges() {
        return privilegesService.getPrivileges();
    }
}
