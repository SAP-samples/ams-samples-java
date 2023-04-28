/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.service.odata;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

import static com.sap.cloud.security.samples.service.odata.CountryEdmProvider.ENTITY_SET_NAME;
import static com.sap.cloud.security.samples.service.odata.CountryEntityCollectionProcessor.countriesCollection;

public class CountryEntityProcessor implements EntityProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CountryEntityProcessor.class);

    private OData odata;
    private ServiceMetadata serviceMetadata;

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataLibraryException {
        List<UriResource> uriResources = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResources.get(0);
        EdmEntitySet requestedEntitySet = uriResourceEntitySet.getEntitySet();
        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();

        final Entity entity = getEntity(requestedEntitySet, keyPredicates);

        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setContent(getSerializedContent(responseFormat, requestedEntitySet, entity));
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    private InputStream getSerializedContent(ContentType responseFormat, EdmEntitySet edmEntitySet, Entity entity) throws SerializerException {
        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
        EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();
        ODataSerializer serializer = odata.createSerializer(responseFormat);

        SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntitySet.getEntityType(), entity, options);
        return serializerResult.getContent();
    }

    private Entity getEntity(EdmEntitySet requestedEntitySet, List<UriParameter> keyPredicates) {
        if (requestedEntitySet.getName().equals(ENTITY_SET_NAME)) {
            LOGGER.info("Predicate value= {}", keyPredicates.get(0).getText());
            String key = keyPredicates.get(0).getText();
            if (key.contains("1")) {
                return countriesCollection.getEntities().get(0);
            } else if (key.contains("2")) {
                return countriesCollection.getEntities().get(1);
            } else if (key.contains("3")) {
                return countriesCollection.getEntities().get(2);
            }
        }
        return new Entity();
    }

    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) {
        // Not implemented for simplification
    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) {
        // Not implemented for simplification
    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) {
        // Not implemented for simplification
    }
}
