/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.samples.filter.PolicyDecisionAuditLogFilter;
import com.sap.cloud.security.test.api.SecurityTestContext;
import com.sap.cloud.security.test.extension.SecurityTestExtension;
import com.sap.cloud.security.token.Token;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;
import static com.sap.cloud.security.config.Service.IAS;
import static org.apache.http.HttpStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JavaServletIntegrationTest {

    @RegisterExtension
    static SecurityTestExtension extension = SecurityTestExtension.forService(IAS).useApplicationServer()
            .addApplicationServletFilter(PolicyDecisionAuditLogFilter.class)
            .addApplicationServlet(JavaServlet.class, JavaServlet.ENDPOINT)
            .addApplicationServlet(JavaServletAdvanced.class, JavaServletAdvanced.ENDPOINT);

    @BeforeAll
    public static void assumePolicyEngineIsReachable() {
        PolicyDecisionPoint pdp = PolicyDecisionPoint.create(DEFAULT);
        assumeTrue(pdp.isReachable());
    }

    @Test
    void requestWithoutAuthorizationHeader_unauthenticated() throws IOException {
        HttpGet request = createGetRequest(null);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_UNAUTHORIZED, response.getStatusLine().getStatusCode()); // 401
        }
    }

    @Test
    void requestWithEmptyAuthorizationHeader_unauthenticated() throws Exception {
        HttpGet request = createGetRequest("");
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_UNAUTHORIZED, response.getStatusLine().getStatusCode()); // 401
        }
    }

    @Test
    void requestWithToken_missingPermission_unauthorized(SecurityTestContext context) throws IOException {
        String jwt = context.getPreconfiguredJwtGenerator().withClaimValues("test_policies", "test.notExisting")
                .createToken().getTokenValue();

        HttpGet request = createGetRequest(jwt);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_FORBIDDEN, response.getStatusLine().getStatusCode()); // 403
        }
    }

    @Test
    void requestWithToken_withPermission_ok(SecurityTestContext context) throws IOException {
        String jwt = context.getPreconfiguredJwtGenerator().withClaimValues("test_policies", "ams.readAllSalesOrders")
                .createToken().getTokenValue();

        HttpGet request = createGetRequest(jwt);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_OK, response.getStatusLine().getStatusCode()); // 200
            assertEquals("Read-protected method called!", EntityUtils.toString(response.getEntity(), "UTF-8"));
        }
    }

    @Test
    void requestWithToken_withCustomPermission(SecurityTestContext context) throws IOException {
        String jwt = context.getPreconfiguredJwtGenerator().withClaimValues("test_policies", "ams.readAllOwnItems")
                .createToken().getTokenValue();

        HttpGet request = createGetRequest(jwt, JavaServletAdvanced.ENDPOINT);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_OK, response.getStatusLine().getStatusCode()); // 200
            assertThat(EntityUtils.toString(response.getEntity(), "UTF-8"),
                    containsString("Retrieved access constraint: eq($app.author.createdBy, \"the-user-id\")"));
        }

        jwt = context.getPreconfiguredJwtGenerator().withClaimValues("test_policies", "ams.readAllOwnItems_new")
                .createToken().getTokenValue();

        request = createGetRequest(jwt, JavaServletAdvanced.ENDPOINT);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_OK, response.getStatusLine().getStatusCode()); // 200
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertThat(responseString,
                    containsString("Retrieved access constraint: or(\n"));
            assertThat(responseString,
                    containsString("  eq($app.author.createdBy, \"the-user-id\")\n"));
            assertThat(responseString,
                    containsString("  eq($app.author.updatedBy, \"the-user-id\")\n"));
            assertThat(responseString,
                    containsString(")"));
        }
    }

    @Test
    void request_withBotUserToken_200() throws Exception {
        Token botUserToken = extension.getContext().getPreconfiguredJwtGenerator()
                .withClaimValue("user_uuid", "d9403e85-2029-46f1-9c09-ee32e881c081")
                .createToken();
        HttpGet request = createGetRequest(botUserToken.getTokenValue(), JavaServletAdvanced.ENDPOINT);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_OK, response.getStatusLine().getStatusCode()); // 200
            assertThat(EntityUtils.toString(response.getEntity(), "UTF-8"),
                    containsString("Read-protected method called. Retrieved access constraint: eq($app.author.createdBy, \"d9403e85-2029-46f1-9c09-ee32e881c081\")"));
        }
    }


    private HttpGet createGetRequest(String accessToken) {
        return createGetRequest(accessToken, JavaServlet.ENDPOINT);
    }

    private HttpGet createGetRequest(String accessToken, String endpoint) {
        HttpGet httpGet = new HttpGet(extension.getContext().getApplicationServerUri() + endpoint);
        httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        httpGet.setHeader("sap_passport", "passport");
        return httpGet;
    }
}