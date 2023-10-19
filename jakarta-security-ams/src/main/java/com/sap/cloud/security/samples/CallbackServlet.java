/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.config.ServiceConstants;
import com.sap.cloud.security.json.DefaultJsonObject;
import com.sap.cloud.security.xsuaa.http.MediaType;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.stream.Collectors;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

@WebServlet(urlPatterns = { CallbackServlet.ENDPOINT })
public class CallbackServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CallbackServlet.class);

    static final long serialVersionUID = 1L;
    static final String ENDPOINT = "/v1.0/callback/zones/*";
    final PolicyDecisionPoint policyDecisionPoint;

    private static final DefaultJsonObject vcapApplication = new DefaultJsonObject(
            System.getenv(ServiceConstants.VCAP_APPLICATION));

    public CallbackServlet() {
        policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse response) {
        LOGGER.info("Callback service with method=PUT called for zone={}", req.getPathInfo());

        try {
            String requestData = req.getReader().lines().collect(Collectors.joining());
            String url = generateUrlForSubscriber(requestData);

            response.setContentType(MediaType.APPLICATION_JSON.value());
            response.getWriter().write(url);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (JSONException | IOException e) {
            LOGGER.error("Couldn't subscribe to the app", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse response) {
        LOGGER.info("Callback service with method=DELETE called for subscription: ownServiceInstance: {}, serviceInstance: {}, planName: {}",
                req.getParameter("ownServiceInstance"), req.getParameter("serviceInstances"),
                req.getParameter("planName"));
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String generateUrlForSubscriber(String requestData) {
        String appUri = vcapApplication.getAsStringList("application_uris").get(0);
        // Required to shorten the url due to character length limitation
        String appRouterUri = appUri.replace("jakarta-ams", "ar");

        LOGGER.debug("Subscription request data {}", requestData);
        JSONObject jsonData = new JSONObject(requestData);
        JSONObject subscriber = jsonData.getJSONObject("subscriber");
        String tenantName = ((String) subscriber.get("tenantHost")).split("\\.")[0];

        String url = String.format("\"https://%s--%s-%s\"", subscriber.get("zoneId"), tenantName, appRouterUri);
        LOGGER.debug("Generated subscription url: {}", url);
        return url;
    }
}
