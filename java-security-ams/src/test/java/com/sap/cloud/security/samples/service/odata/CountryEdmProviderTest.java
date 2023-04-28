/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.service.odata;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.sap.cloud.security.samples.service.odata.CountryEdmProvider.*;
import static org.assertj.core.api.Assertions.assertThat;

class CountryEdmProviderTest {

    CountryEdmProvider cut;

    @BeforeEach
    void setUp() {
        cut = new CountryEdmProvider();
    }

    @Test
    void getEntityType() {
        assertThat(cut.getEntityType(ENTITY_TYPE_FQN).getName()).isEqualTo("Country");
        assertThat(cut.getEntityType(new FullQualifiedName("some-namespace", "no-name"))).isNull();
    }

    @Test
    void getEntitySet() {
        assertThat(cut.getEntitySet(CONTAINER_FQN, ENTITY_SET_NAME).getName()).isEqualTo("Country");
        assertThat(cut.getEntitySet(new FullQualifiedName("some-namespace", "no-name"), "some-name")).isNull();
    }

    @Test
    void getEntityContainerInfo() {
        assertThat(cut.getEntityContainerInfo(CONTAINER_FQN).getContainerName().getName()).isEqualTo("CountryContainer");
        assertThat(cut.getEntityContainerInfo(new FullQualifiedName("some-namespace", "no-name"))).isNull();
    }

    @Test
    void getSchemas() {
        assertThat(cut.getSchemas().get(0).getEntityType("Country")).isNotNull();
        assertThat(cut.getSchemas().get(0).getEntityType("some-name")).isNull();
    }
}