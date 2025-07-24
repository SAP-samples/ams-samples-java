/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(HealthServlet.ENDPOINT)
public class HealthServlet extends HttpServlet {
  static final long serialVersionUID = 1L;
  static final String ENDPOINT = "/health";
  final PolicyDecisionPoint policyDecisionPoint;
  private static final Logger LOGGER = LoggerFactory.getLogger(HealthServlet.class);
  final HttpClient httpClient;

  public HealthServlet()
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

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    if (X509SourceSingletonWrapper.isSvidAvailable()) {
      LOGGER.info("Health check successful.");
      response.setContentType("text/plain");
      writeLine(response, policyDecisionPoint.getHealthStatus().toString());
      response.setStatus(HttpServletResponse.SC_OK);
    } else {
      LOGGER.warn("Health check failed. SvidSslContext not initialized.");
      response.setStatus(503);
    }
  }

  private void writeLine(final HttpServletResponse response, final String string)
      throws IOException {
    response.getWriter().append(string);
    response.getWriter().append("\n");
  }
}
