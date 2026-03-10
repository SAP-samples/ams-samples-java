package com.sap.cloud.security.ams.samples.auth;

import com.sap.cloud.security.ams.api.Privilege;
import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
    ANYONE(),
    AUTHENTICATED(),
    READ_PRODUCTS("read", "products"),
    READ_ORDERS("read", "orders"),
    CREATE_ORDERS("create", "orders"),
    DELETE_ORDERS("delete", "orders");

    private final String action;
    private final String resource;
    private final Privilege privilege;

    Role(String action, String resource) {
        this.action = action;
        this.resource = resource;
        this.privilege = Privilege.of(action, resource);
    }

    Role() {
        this(null, null);
    }

    public String getAction() {
        if (action == null) {
            throw new UnsupportedOperationException(String.format("Role %s does not have an action", this.name()));
        }

        return action;
    }

    public String getResource() {
        if (resource == null) {
            throw new UnsupportedOperationException(String.format("Role %s does not have a resource", this.name()));
        }

        return resource;
    }

    public Privilege asPrivilege() {
        return privilege;
    }
}
