package com.sap.cloud.security.ams.samples.service;

import java.util.Set;

import com.sap.cloud.security.ams.samples.auth.AuthHandler;
import com.sap.cloud.security.ams.api.*;
import com.sap.cloud.security.token.*;

import org.slf4j.*;

import io.javalin.http.Handler;

/**
 * Service for handling user privileges information
 */
public class PrivilegesService {
    private static final Logger LOG = LoggerFactory.getLogger(PrivilegesService.class);
    private AuthHandler authHandler;

    public PrivilegesService(AuthHandler authHandler) {
        this.authHandler = authHandler;
    }

    public Handler getPrivileges() {
        return ctx -> {
            LOG.debug("Processing GET /privileges request");

            try {
                Authorizations authorizations = authHandler.getAuthorizations();
                Set<Privilege> privileges = authorizations.getPotentialPrivileges();

                LOG.info("Privileges for user {}: {}",
                        SecurityContext.getToken().getClaimAsString(TokenClaims.SAP_GLOBAL_SCIM_ID), privileges);
                ctx.json(privileges);
            } catch (Exception e) {
                LOG.error("Error retrieving privileges", e);
                ctx.status(500).result("Internal server error");
            }
        };
    }
}
