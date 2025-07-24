/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.dcl.client.el.FilterClause;
import com.sap.cloud.security.ams.dcl.client.pdp.Attributes;
import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.samples.filter.IasSecurityFilter;
import com.sap.cloud.security.samples.ztis.mtls.X509SourceSingletonWrapper;
import io.spiffe.exception.SocketEndpointAddressException;
import io.spiffe.exception.X509SourceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;

@WebServlet(JavaServletAdvanced.ENDPOINT)
public class JavaServletAdvanced extends HttpServlet {
  static final long serialVersionUID = 1L;
  static final String ENDPOINT = "/api/advanced";
  private final PolicyDecisionPoint policyDecisionPoint;
  HttpClient httpClient;

  public JavaServletAdvanced()
      throws GeneralSecurityException,
          IOException,
          SocketEndpointAddressException,
          X509SourceException {
    httpClient =
        HttpClient.newBuilder()
            .sslContext(X509SourceSingletonWrapper.getInstance().getSslContextInstance())
            .build();
    policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT, "httpClient", httpClient);
  }

  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  @SuppressWarnings("squid:S1166")
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Principal principal = Principal.create();
    Attributes attributes =
        principal
            .getAttributes()
            .setAction("read")
            .setResource("salesOrders")
            .setUnknowns("author", "Country");

    FilterClause clause = policyDecisionPoint.allowFilterClause(attributes);
    if (clause.isDenied()) {
      IasSecurityFilter.sendUnauthorizedResponse(response, attributes);
      return;
    }
    response.setContentType("text/plain");
    response
        .getWriter()
        .write(
            "Read-protected method called. Retrieved access constraint: "
                + clause.getCondition()
                + "\n");
    if (X509SourceSingletonWrapper.isSvidAvailable()) {
      response
          .getWriter()
          .write("Svid available (Svid has been successfully retrieved via Workload API).");
    } else {
      response.getWriter().write("No Svid available (No Svid has been retrieved via Workload API)");
    }
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
