/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.config.ServiceConstants;
import com.sap.cloud.security.json.DefaultJsonObject;
import com.sap.cloud.security.json.JsonObject;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

@WebServlet(AmsUiServlet.ENDPOINT)
public class AmsUiServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmsUiServlet.class);

    HttpClient httpClient = HttpClient.newHttpClient();
    private static final OAuth2ServiceConfiguration serviceConfig = Environments.getCurrent().getIasConfiguration();
    static final long serialVersionUID = 1L;
    static final String ENDPOINT = "/uiurl";

    final PolicyDecisionPoint policyDecisionPoint;

    private static final DefaultJsonObject vcapServices = new DefaultJsonObject(
            System.getenv(ServiceConstants.VCAP_SERVICES));

    public AmsUiServlet() {
        policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT);
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    @SuppressWarnings("squid:S1166")
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String forwardedHeader = request.getHeader("x-forwarded-host");
        LOGGER.debug("x-forwarded-host = {}", forwardedHeader);
        if (forwardedHeader == null || forwardedHeader.isEmpty()) {
            String iasUrl = serviceConfig.getUrl().toString();
            response.getWriter().write(iasUrl + "/admin");
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        String subdomain = forwardedHeader.substring(0, forwardedHeader.indexOf(System.getenv("SUBSCRIPTION_SUFFIX")));

        JsonObject serviceJsonObject = vcapServices.getJsonObjects("subscription-manager").get(0);
        Map<String, String> credentialsMap = serviceJsonObject.getJsonObject("credentials").getKeyValueMap();
        String uaaDomain = credentialsMap.get("uaadomain");

        String url = String.format("https://api.%s/sap/rest/tenantLoginInfo/%s?iasClient=%s", uaaDomain, subdomain, serviceConfig.getClientId());
        LOGGER.debug("tenant info url = {}", url);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url)).GET().build();

        try {
            // Send the request and retrieve the response
            HttpResponse<String> xsuaaResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (xsuaaResponse.statusCode() != 200) {
                LOGGER.error(String.format("unexpected response status from tenant info endpoint: %s", xsuaaResponse.statusCode()));
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            JSONObject jsonData = new JSONObject(xsuaaResponse.body());
            if (!Objects.equals(jsonData.getString("status"), "ACTIVE") || !Objects.equals(jsonData.getString("authentication"), "ias")) {
                LOGGER.error(String.format("unexpected response from tenant info endpoint: %s", xsuaaResponse.body()));
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }


            String iasUrl = jsonData.getString("authorization_endpoint").substring(0, jsonData.getString("authorization_endpoint").indexOf("/", 8)); // skip https://

            response.getWriter().write(iasUrl + "/admin");
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            LOGGER.error("unexpected error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
