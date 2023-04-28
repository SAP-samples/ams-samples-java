/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.service.odata;

import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;

import static com.sap.cloud.security.samples.service.odata.CountryEdmProvider.ENTITY_TYPE_FQN;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

class CountryEntityCollectionProcessorTest {

    private static CountryEntityCollectionProcessor cut;
    private static final UriInfo uriMock = Mockito.mock(UriInfo.class);
    private static final UriResourceEntitySet uriResourceMock = Mockito.mock(UriResourceEntitySet.class);
    private static final EdmEntitySet edmEntitySetMock = Mockito.mock(EdmEntitySet.class);
    private static final FilterOption filterOptionMock = Mockito.mock(FilterOption.class);

    @BeforeAll
    static void beforeAll() {
        OData odata = OData.newInstance();
        ServiceMetadata serviceMetadata = odata.createServiceMetadata(new CountryEdmProvider(), new ArrayList<>());
        cut = new CountryEntityCollectionProcessor();
        cut.init(odata, serviceMetadata);

        when(edmEntitySetMock.getName()).thenReturn("Country");
        when(edmEntitySetMock.getEntityType()).thenReturn(serviceMetadata.getEdm().getEntityType(ENTITY_TYPE_FQN));
        when(uriResourceMock.getEntitySet()).thenReturn(edmEntitySetMock);
        when(uriMock.getUriResourceParts()).thenReturn(Collections.singletonList(uriResourceMock));
    }

    @Test
    void readEntityCollection_filtered1() throws ODataLibraryException {
        ODataResponse response = new ODataResponse();
        when(filterOptionMock.getText()).thenReturn("$filter=id eq 'ie' or name eq 'ie'");
        when(uriMock.getFilterOption()).thenReturn(filterOptionMock);


        cut.readEntityCollection(new ODataRequest(), response, uriMock, ContentType.APPLICATION_JSON);
        JSONArray responseValue = new JSONObject(new JSONTokener(response.getContent())).getJSONArray("value");

        assertThat(responseValue.length()).isEqualTo(1);
        assertThat(responseValue.getJSONObject(0).get("name")).isEqualTo("Ireland");
    }

    @Test
    void readEntityCollection_filtered2() throws ODataLibraryException {
        ODataResponse response = new ODataResponse();
        when(filterOptionMock.getText()).thenReturn("$filter=id eq 'i' or name eq 'i'");
        when(uriMock.getFilterOption()).thenReturn(filterOptionMock);

        cut.readEntityCollection(new ODataRequest(), response, uriMock, ContentType.APPLICATION_JSON);
        JSONArray responseValue = new JSONObject(new JSONTokener(response.getContent())).getJSONArray("value");

        assertThat(responseValue.length()).isEqualTo(2);
        assertThat(responseValue).hasToString("[{\"name\":\"Ireland\",\"ID\":\"IE\"},{\"name\":\"Spain\",\"ID\":\"ES\"}]");
    }

    @Test
    void readEntityCollection_not_filtered() throws ODataLibraryException {
        ODataResponse response = new ODataResponse();
        when(uriMock.getFilterOption()).thenReturn(null);

        cut.readEntityCollection(new ODataRequest(), response, uriMock, ContentType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject(new JSONTokener(response.getContent()));

        assertThat(jsonObject.getJSONArray("value").length()).isEqualTo(3);
    }

}