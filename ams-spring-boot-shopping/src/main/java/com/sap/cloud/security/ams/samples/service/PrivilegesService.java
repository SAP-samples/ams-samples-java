package com.sap.cloud.security.ams.samples.service;

import java.util.Set;

import com.sap.cloud.security.ams.api.*;
import com.sap.cloud.security.token.*;

import org.slf4j.*;
import org.springframework.stereotype.Service;

/**
 * Service for handling user privileges information
 */
@Service
public class PrivilegesService {
    private static final Logger logger = LoggerFactory.getLogger(PrivilegesService.class);
    private final Authorizations authorizations;

    public PrivilegesService(Authorizations authorizations) {
        this.authorizations = authorizations;
    }

    /**
     * Get the potential privileges for the current user
     * 
     * <p>Returns all privileges that the user could potentially have access to,
     * regardless of contextual conditions.
     */
    public Set<Privilege> getPrivileges() {
        Set<Privilege> privileges = authorizations.getPotentialPrivileges();

        String userId = SecurityContext.getToken().getClaimAsString(TokenClaims.SAP_GLOBAL_SCIM_ID);
        logger.info("Privileges for user {}: {}", userId, privileges);

        return privileges;
    }
}
