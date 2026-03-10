/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.dcl.client.pdp.Attributes;
import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.samples.filter.IasSecurityFilter;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

@WebServlet(JavaServlet.ENDPOINT)
public class JavaServlet extends HttpServlet {
  static final long serialVersionUID = 1L;
  static final String ENDPOINT = "/api/read";
  final PolicyDecisionPoint policyDecisionPoint;

  public JavaServlet() {
    policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT);
  }

  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  @java.lang.SuppressWarnings("squid:S1166")
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Attributes attributes =
        Principal.create().getAttributes().setAction("read").setResource("salesOrders");
    if (!policyDecisionPoint.allow(attributes)) {
      IasSecurityFilter.sendUnauthorizedResponse(response, attributes);
      return;
    }
    // ===== WRITE APPLICATION's audit log ===========
    response.setContentType("text/plain");
    response.getWriter().write("Read-protected method called!");
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
