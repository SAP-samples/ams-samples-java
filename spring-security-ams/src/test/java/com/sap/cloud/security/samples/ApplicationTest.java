/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.test.extension.SecurityTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.sap.cloud.security.config.Service.IAS;
import static com.sap.cloud.security.samples.BasicControllerTest.MOCK_SERVER_PORT;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class ApplicationTest {

  @RegisterExtension
  static SecurityTestExtension extension =
      SecurityTestExtension.forService(IAS).setPort(MOCK_SERVER_PORT); //

  @Test
  void whenLoadApplication_thenSuccess() {
    assertTrue(true);
  }
}
