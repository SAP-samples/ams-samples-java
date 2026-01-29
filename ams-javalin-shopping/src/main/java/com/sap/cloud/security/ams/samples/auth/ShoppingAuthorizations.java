package com.sap.cloud.security.ams.samples.auth;

import com.sap.cloud.security.ams.api.Authorizations;
import com.sap.cloud.security.ams.api.Decision;

import java.util.Map;

import static com.sap.cloud.security.ams.samples.auth.AmsAttributes.ORDER_CREATED_BY;
import static com.sap.cloud.security.ams.samples.auth.Role.*;

public class ShoppingAuthorizations {

    private final Authorizations authorizations;

    private ShoppingAuthorizations(Authorizations authorizations) {
        this.authorizations = authorizations;
    }

    public Authorizations getBaseAuthorizations() {
        return authorizations;
    }

    public static ShoppingAuthorizations of(Authorizations authorizations) {
        return new ShoppingAuthorizations(authorizations);
    }

    public Decision checkCreateOrder(String productCategory, double totalAmount) {
        return authorizations.checkPrivilege(
                CREATE_ORDERS.getAction(),
                CREATE_ORDERS.getResource(),
                Map.of(
                        AmsAttributes.PRODUCT_CATEGORY, productCategory,
                        AmsAttributes.ORDER_TOTAL, totalAmount));
    }

    public Decision checkReadOrder(String createdBy) {
        return authorizations.checkPrivilege(
                READ_ORDERS.getAction(),
                READ_ORDERS.getResource(),
                Map.of(
                        ORDER_CREATED_BY, createdBy));
    }

    public Decision checkRole(Role role) {
        return authorizations.checkPrivilege(role.getAction(), role.getResource());
    }
}
