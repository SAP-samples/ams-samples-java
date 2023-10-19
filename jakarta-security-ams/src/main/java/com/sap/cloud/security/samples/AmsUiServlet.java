/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

@WebServlet(AmsUiServlet.ENDPOINT)
public class AmsUiServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmsUiServlet.class);

    private static final OAuth2ServiceConfiguration serviceConfig = Environments.getCurrent().getIasConfiguration();
    private final String amsUiUrl;
    static final long serialVersionUID = 1L;
    static final String ENDPOINT = "/uiurl";

    final PolicyDecisionPoint policyDecisionPoint;

    public AmsUiServlet() {
       this(serviceConfig.getProperties().get("authorization_ui_url"));
    }
    public AmsUiServlet(String amsUiUrl) {
        policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT);
        this.amsUiUrl = amsUiUrl;
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    @SuppressWarnings("squid:S1166")
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // x-forwarded-host(subscriber) value pattern: https://<ias tenant id>--<ias tenant name>-ar-<user ID>.<landscape domain>
        // AMS admin UI pattern: https://<ias tenant id>--<ias tenant name>.authorization.<landscape domain>
        String forwardedHeader = request.getHeader("x-forwarded-host");
        LOGGER.debug("x-forwarded-host = {}", forwardedHeader);

        if (forwardedHeader.isEmpty()) {
            response.getWriter().write(amsUiUrl);
        } else {
            String subscriberTenantInfo =  forwardedHeader.replaceAll("(-ar-)(.*)$", ".");
            String subscriberAmsUiUrl = amsUiUrl.replaceAll("^(.*?)\\.", subscriberTenantInfo);
            LOGGER.debug("subscriber AMS UI URL = {}", subscriberAmsUiUrl);

            response.getWriter().write("https://" + subscriberAmsUiUrl);
        }
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_OK);
    }

}
