/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.test.JwtGenerator;
import com.sap.cloud.security.test.extension.SecurityTestExtension;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static com.sap.cloud.security.ams.spring.test.resourceserver.MockOidcTokenRequestPostProcessor.userWithPolicies;
import static com.sap.cloud.security.ams.spring.test.resourceserver.MockOidcTokenRequestPostProcessor.userWithoutPolicies;
import static com.sap.cloud.security.ams.spring.test.resourceserver.MockClientTokenRequestPostProcessor.client;
import static com.sap.cloud.security.config.Service.IAS;
import static com.sap.cloud.security.x509.X509Constants.FWD_CLIENT_CERT_HEADER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BasicControllerTest {

    static final int MOCK_SERVER_PORT = ThreadLocalRandom.current().nextInt(49152, 50000);

    @RegisterExtension
    static SecurityTestExtension extension = SecurityTestExtension.forService(IAS).setPort(MOCK_SERVER_PORT); // optionally

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticateWithoutPermission_200() throws Exception {
        mockMvc.perform(get("/authenticate").with(userWithoutPolicies(extension.getContext())))
                .andExpect(status().isOk());
    }

    @Test
    void healthAsAnonymous_200() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void readWithoutPermision_403() throws Exception {
        mockMvc.perform(get("/read").with(userWithoutPolicies(extension.getContext())))
                .andExpect(status().isForbidden());
    }

    @Test
    void readWithPermision_200() throws Exception {
        mockMvc.perform(get("/read").with(userWithPolicies(extension.getContext(), "common.readAll")))
                .andExpect(status().isOk());
    }

    @Test
    void tokenExpired_401() throws Exception {
        JwtGenerator jwtGenerator = extension.getContext().getPreconfiguredJwtGenerator();
        jwtGenerator.withExpiration(Instant.now().minusSeconds(1000));

        mockMvc.perform(get("/authenticate").with(userWithoutPolicies(jwtGenerator)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewWithoutPermision_403() throws Exception {
        mockMvc.perform(get("/authorized").with(userWithoutPolicies(extension.getContext())))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewWithPermision_200() throws Exception {
        mockMvc.perform(get("/authorized").with(userWithPolicies(extension.getContext(), "common.viewAll")))
                .andExpect(status().isOk());
    }

    @Test
    void readAsAnonymous_401() throws Exception {
        mockMvc.perform(get("/authenticate")).andExpect(status().isUnauthorized());
    }

    @Test
    void accessAsTechnicalUser_200() throws Exception {
        String certHeader = IOUtils.resourceToString("/cert-header.txt", StandardCharsets.UTF_8);
        mockMvc.perform(get("/technical-communication").header(FWD_CLIENT_CERT_HEADER, certHeader)
                .with(client(extension.getContext().getPreconfiguredJwtGenerator()))).andExpect(status().isOk());
    }

    @Test
    void accessAsTechnicalUser_401() throws Exception {
        String certHeader = IOUtils.resourceToString("/unaccepted-cert-header.txt", StandardCharsets.UTF_8);
        mockMvc.perform(get("/technical-communication").header(FWD_CLIENT_CERT_HEADER, certHeader)
                .with(client(extension.getContext().getPreconfiguredJwtGenerator())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessAsTechnicalUser_woCertHeader_401() throws Exception {
        mockMvc.perform(
                get("/technical-communication").with(client(extension.getContext().getPreconfiguredJwtGenerator())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessAsUser_woCertHeader_403() throws Exception {
        mockMvc.perform(get("/technical-communication")
                .with(userWithoutPolicies(extension.getContext().getPreconfiguredJwtGenerator())))
                .andExpect(status().isForbidden());
    }
}
