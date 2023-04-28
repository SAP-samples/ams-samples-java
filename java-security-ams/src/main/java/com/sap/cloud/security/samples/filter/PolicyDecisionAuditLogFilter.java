/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.filter;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.ams.logging.PolicyEvaluationV2AuditLogger;
import com.sap.cloud.security.config.cf.CFEnvironment;
import com.sap.xs.audit.api.exception.AuditLogException;
import com.sap.xs.audit.api.v2.AuditLogMessageFactory;
import com.sap.xs.audit.api.v2.AuditedDataSubject;
import com.sap.xs.audit.client.impl.v2.AuditLogMessageFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;
import static com.sap.cloud.security.ams.logging.PolicyEvaluationV2AuditLogger.MDC_SAP_PASSPORT;

@WebFilter(filterName = "ams-audit-logging")
public class PolicyDecisionAuditLogFilter implements Filter {
    private PolicyDecisionPoint policyDecisionPoint;
    PolicyEvaluationV2AuditLogger auditLogger;
    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyDecisionAuditLogFilter.class);
    AuditLogMessageFactory auditLogFactory;

    public PolicyDecisionAuditLogFilter() {
        policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT);
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
        if (request instanceof HttpServletRequest) {
            MDC.put(MDC_SAP_PASSPORT, ((HttpServletRequest) request).getHeader("sap_passport"));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("sap-passport");
        }
    }

    /**
     * Provides the audited data subject, which is the owner of the modified personal data, audit logged with this
     * event. A data subject (id and type) is mandatory for writing an audit log entry.
     *
     * @return the subject of the accessed data
     */
    private AuditedDataSubject getSubjectOfAmsData() {
        AuditedDataSubject auditedDataSubject = auditLogFactory.createAuditedDataSubject();
        if (CFEnvironment.getInstance().getIasConfiguration() == null) {
            return auditedDataSubject;
        }
        auditedDataSubject.addIdentifier("client_id", CFEnvironment.getInstance().getIasConfiguration().getClientId());
        auditedDataSubject.setType("application-java-security");
        return auditedDataSubject;
    }
}
