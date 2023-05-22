/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.test.extension.SecurityTestExtension;
import com.sap.cloud.security.token.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.ThreadLocalRandom;

import static com.sap.cloud.security.ams.spring.test.resourceserver.MockOidcTokenRequestPostProcessor.userWithPolicies;
import static com.sap.cloud.security.ams.spring.test.resourceserver.MockOidcTokenRequestPostProcessor.userWithoutPolicies;
import static com.sap.cloud.security.config.Service.IAS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderControllerTest {
    // https://docs.spring.io/spring-security/site/docs/current/reference/html5/#testing-oauth2-login
    static final int MOCK_SERVER_PORT = ThreadLocalRandom.current().nextInt(49152, 50000);

    @RegisterExtension
    static SecurityTestExtension extension = SecurityTestExtension.forService(IAS).setPort(MOCK_SERVER_PORT);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void read_403() throws Exception {
        mockMvc.perform(get("/salesOrders").with(userWithoutPolicies(extension.getContext())))
                .andExpect(status().isForbidden());
    }

    @Test
    void read_200() throws Exception {
        mockMvc.perform(get("/salesOrders").with(userWithPolicies(extension.getContext(), "sales.adminAllSales")))
                .andExpect(status().isOk());
    }

    @Test
    void readSalesOrders_Type_200() throws Exception {
        mockMvc.perform(get("/salesOrders/readByCountryAndType/IT/101")
                .with(userWithPolicies(extension.getContext(), "sales.readSalesOrders_Type")))
                .andExpect(status().isOk());
    }

    @Test
    void readSalesOrders_Type_403() throws Exception {
        mockMvc.perform(get("/salesOrders/readByCountryAndType/IT/501")
                .with(userWithPolicies(extension.getContext(), "sales.readSalesOrders_Type")))
                .andExpect(status().isForbidden());
    }

    @Test
    void readByCountry_200() throws Exception {
        mockMvc.perform(get("/salesOrders/readByCountry/IT")
                .with(userWithPolicies(extension.getContext(), "common.readAll_Europe"))).andExpect(status().isOk());

        mockMvc.perform(
                get("/salesOrders/readByCountry/IT").with(userWithPolicies(extension.getContext(), "common.readAll")))
                .andExpect(status().isOk());
    }

    @Test
    void readByCountry_403() throws Exception {
        mockMvc.perform(get("/salesOrders/readByCountry/US")
                .with(userWithPolicies(extension.getContext(), "common.readAll_Europe")))
                .andExpect(status().isForbidden());
    }

    @Test
    void readByCountryWithEmptyCountryCode_403() throws Exception {
        mockMvc.perform(get("/salesOrders/readByCountry/ ")
                .with(userWithPolicies(extension.getContext(), "common.readAll_Europe")))
                .andExpect(status().isForbidden());
    }

}
