package com.sap.cloud.security.samples;

import com.sap.cloud.security.test.api.SecurityTestContext;
import com.sap.cloud.security.test.extension.SecurityTestExtension;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static com.sap.cloud.security.config.Service.IAS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueHelpServletTest {

    private static final String VALUE_HELP_ENDPOINT = "/app/callback/value-help/";
    private static String jwt;

    @RegisterExtension
    static SecurityTestExtension extension = SecurityTestExtension.forService(IAS).useApplicationServer()
            .addApplicationServlet(ValueHelpServlet.class, ValueHelpServlet.ENDPOINT);

    @BeforeAll
    static void beforeAll(SecurityTestContext context) {
        jwt = context.getPreconfiguredJwtGenerator().withClaimValues("test_policies", "ams.readAllSalesOrders")
                .createToken().getTokenValue();
    }

    @Test
    void request_unauthenticated() throws IOException {
        HttpGet request = createGetRequest(null, VALUE_HELP_ENDPOINT);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
        }
    }

    @ParameterizedTest
    @CsvSource({ "$metadata, <EntityContainer Name=\"CountryContainer\"><EntitySet Name=\"Country\" EntityType=\"com.sap.cloud.security.Country\">",
    "Country, $metadata#Country\",\"value\":[{\"ID\":\"IE\",\"name\":\"Ireland\"},{\"ID\":\"DE\",\"name\":\"Germany\"},{\"ID\":\"ES\",\"name\":\"Spain\"}]",
    "Country('1'), {\"@odata.context\":\"$metadata#Country\",\"ID\":\"IE\",\"name\":\"Ireland\"}"})
    void valueHelpEndpointRequests(String endpoint, String expected) throws IOException {
        HttpGet request = createGetRequest(jwt, VALUE_HELP_ENDPOINT + endpoint);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            assertEquals(SC_OK, response.getStatusLine().getStatusCode());
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertThat(responseBody, containsString(expected));
        }

    }

    private HttpGet createGetRequest(String accessToken, String endpoint) {
        HttpGet httpGet = new HttpGet(extension.getContext().getApplicationServerUri() + endpoint);
        httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return httpGet;
    }
}
