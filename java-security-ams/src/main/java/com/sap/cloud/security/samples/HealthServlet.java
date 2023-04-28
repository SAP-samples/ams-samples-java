/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.xsuaa.http.MediaType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint.Parameters.STARTUP_HEALTH_CHECK_TIMEOUT;
import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

@WebServlet(HealthServlet.ENDPOINT)
public class HealthServlet extends HttpServlet {
    static final long serialVersionUID = 1L;
    static final String ENDPOINT = "/health";
    final PolicyDecisionPoint policyDecisionPoint;

    public HealthServlet() {
        policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT, STARTUP_HEALTH_CHECK_TIMEOUT, 10L);
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    @SuppressWarnings("squid:S1166")
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON.value());
        response.getWriter().write(policyDecisionPoint.getHealthStatus().toString());
        response.setStatus(HttpServletResponse.SC_OK);
    }

}
