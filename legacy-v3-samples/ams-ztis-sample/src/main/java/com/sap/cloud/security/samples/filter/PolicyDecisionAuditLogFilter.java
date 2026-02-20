/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.filter;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;
import static com.sap.cloud.security.ams.logging.PolicyEvaluationV2AuditLogger.MDC_SAP_PASSPORT;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.ams.logging.PolicyEvaluationV2AuditLogger;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.samples.ztis.mtls.X509SourceSingletonWrapper;
import com.sap.xs.audit.api.exception.AuditLogException;
import com.sap.xs.audit.api.v2.AuditLogMessageFactory;
import com.sap.xs.audit.api.v2.AuditedDataSubject;
import com.sap.xs.audit.client.impl.v2.AuditLogMessageFactoryImpl;
import io.spiffe.exception.SocketEndpointAddressException;
import io.spiffe.exception.X509SourceException;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@WebFilter(filterName = "ams-audit-logging", urlPatterns = "/app/*")
public class PolicyDecisionAuditLogFilter implements Filter {
  private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionAuditLogFilter.class);
  private final PolicyDecisionPoint policyDecisionPoint;
  PolicyEvaluationV2AuditLogger auditLogger;
  AuditLogMessageFactory auditLogFactory;
  public static final OAuth2ServiceConfiguration serviceConfig =
      Environments.getCurrent().getIasConfiguration();
  HttpClient httpClient;

  public PolicyDecisionAuditLogFilter()
      throws GeneralSecurityException,
          IOException,
          SocketEndpointAddressException,
          X509SourceException {

    httpClient =
        HttpClient.newBuilder()
            .sslContext(X509SourceSingletonWrapper.getInstance().getSslContextInstance())
            .build();

    policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT, "httpClient", httpClient);

    try {
      auditLogFactory = new AuditLogMessageFactoryImpl();
      auditLogger = new PolicyEvaluationV2AuditLogger(auditLogFactory, getSubjectOfAmsData());
      policyDecisionPoint.registerListener(auditLogger);
      LOGGER.debug("Successfully registered listener to write audit logs for policy evaluations.");
    } catch (AuditLogException e) {
      throw new IllegalStateException("Unable to initialize AuditLogMessageFactoryImpl", e);
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest servletRequest) {
      MDC.put(MDC_SAP_PASSPORT, servletRequest.getHeader("sap_passport"));
    }
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("sap-passport");
    }
  }

  /**
   * Provides the audited data subject, which is the owner of the modified personal data, audit
   * logged with this event. A data subject (id and type) is mandatory for writing an audit log
   * entry.
   *
   * @return the subject of the accessed data
   */
  private AuditedDataSubject getSubjectOfAmsData() {
    AuditedDataSubject auditedDataSubject = auditLogFactory.createAuditedDataSubject();
    if (Environments.getCurrent().getIasConfiguration() == null) {
      return auditedDataSubject;
    }
    auditedDataSubject.addIdentifier(
        "client_id", Environments.getCurrent().getIasConfiguration().getClientId());
    auditedDataSubject.setType("application-java-security");
    return auditedDataSubject;
  }
}
