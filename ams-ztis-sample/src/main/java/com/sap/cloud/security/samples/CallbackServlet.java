/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.samples.ztis.mtls.X509SourceSingletonWrapper;
import com.sap.cloud.security.xsuaa.http.MediaType;
import io.spiffe.exception.SocketEndpointAddressException;
import io.spiffe.exception.X509SourceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = {CallbackServlet.ENDPOINT})
public class CallbackServlet extends HttpServlet {

  static final long serialVersionUID = 1L;
  static final String ENDPOINT = "/v1.0/callback/tenants/*";
  private static final Logger LOGGER = LoggerFactory.getLogger(CallbackServlet.class);
  final PolicyDecisionPoint policyDecisionPoint;
  HttpClient httpClient;

  public CallbackServlet()
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
  protected void doPut(HttpServletRequest req, HttpServletResponse response) {
    LOGGER.info("Callback service with method=PUT called for zone={}", req.getPathInfo());

    try {
      String url = generateUrlForSubscriber(req);

      JSONObject jsonResponse = new JSONObject();
      jsonResponse.put("applicationURL", url);

      response.setContentType(MediaType.APPLICATION_JSON.value());
      response.getWriter().write(jsonResponse.toString());
      response.setStatus(HttpServletResponse.SC_OK);
    } catch (JSONException | IOException e) {
      LOGGER.error("Couldn't subscribe to the app", e);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse response) {
    LOGGER.info(
        "Callback service with method=DELETE called for subscription: ownServiceInstance: {}, serviceInstance: {}, planName: {}",
        req.getParameter("ownServiceInstance"),
        req.getParameter("serviceInstances"),
        req.getParameter("planName"));
    response.setStatus(HttpServletResponse.SC_OK);
  }

  private String generateUrlForSubscriber(HttpServletRequest request) throws IOException {
    // Required to shorten the url due to character length limitation
    String appRouterSuffix =
        request.getServerName().replaceFirst("^[^.]+", System.getenv("SUBSCRIPTION_SUFFIX"));

    String requestData = request.getReader().lines().collect(Collectors.joining());
    LOGGER.debug("Subscription request data {}", requestData);
    JSONObject jsonData = new JSONObject(requestData);
    JSONObject subscriber = jsonData.getJSONObject("subscriber");

    String url =
        String.format("https://%s%s", subscriber.get("subaccountSubdomain"), appRouterSuffix);
    LOGGER.debug("Generated subscription url: {}", url);
    return url;
  }
}
