/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.dcl.client.el.FilterClause;
import com.sap.cloud.security.ams.dcl.client.pdp.Attributes;
import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.samples.filter.IasSecurityFilter;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

@WebServlet(JavaServletAdvanced.ENDPOINT)
public class JavaServletAdvanced extends HttpServlet {
    static final long serialVersionUID = 1L;
    static final String ENDPOINT = "/app/java-security";
    private final PolicyDecisionPoint policyDecisionPoint;

    public JavaServletAdvanced() {
        policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT);
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    @SuppressWarnings("squid:S1166")
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Principal principal = Principal.create();
        Attributes attributes = principal.getAttributes()
                .setAction("read").setResource("salesOrders")
                .setUnknowns("author");

        FilterClause clause = policyDecisionPoint.allowFilterClause(attributes);
        if (clause.isDenied()) {
            IasSecurityFilter.sendUnauthorizedResponse(response, attributes);
            return;
        }
        response.setContentType("text/plain");
        response.getWriter()
                .write("Read-protected method called. Retrieved access constraint: " + clause.getCondition());
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
