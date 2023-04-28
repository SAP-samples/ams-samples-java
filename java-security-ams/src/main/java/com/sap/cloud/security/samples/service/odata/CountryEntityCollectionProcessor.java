/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.service.odata;

import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class CountryEntityCollectionProcessor  implements EntityCollectionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CountryEntityCollectionProcessor.class);

    private OData odata;
    private ServiceMetadata serviceMetadata;
    protected static final EntityCollection countriesCollection = new EntityCollection();

    public static final String COUNTRIES = "Countries";

    static {
        List<Entity> countries = countriesCollection.getEntities();
        final Entity e1 = new Entity()
                .addProperty(new Property(null, "ID", ValueType.PRIMITIVE, "IE"))
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "Ireland"));
        e1.setId(createId(COUNTRIES, 1));
        countries.add(e1);

        final Entity e2 = new Entity()
                .addProperty(new Property(null, "ID", ValueType.PRIMITIVE, "DE"))
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "Germany"));
        e2.setId(createId(COUNTRIES, 2));
        countries.add(e2);

        final Entity e3 = new Entity()
                .addProperty(new Property(null, "ID", ValueType.PRIMITIVE, "ES"))
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "Spain"));
        e3.setId(createId(COUNTRIES, 3));
        countries.add(e3);
    }

    @Override
    public void init(OData odata, ServiceMetadata metadata) {
        this.odata = odata;
        this.serviceMetadata = metadata;
    }

    @Override
    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataLibraryException {
        List<UriResource> uriResources = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResources.get(0);
        EdmEntitySet requestedEntitySet = uriResourceEntitySet.getEntitySet();
        LOGGER.info("Reading entitySet of {}", requestedEntitySet);

        EntityCollection entitySet = filterEntities(uriInfo, requestedEntitySet);

        response.setContent(getSerializedContent(request, responseFormat, requestedEntitySet, entitySet));
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    private InputStream getSerializedContent(ODataRequest request, ContentType responseFormat, EdmEntitySet requestedEntitySet, EntityCollection entitySet) throws SerializerException {
        //prepare the response in the requested format
        ODataSerializer serializer = odata.createSerializer(responseFormat);

        ContextURL contextUrl = ContextURL.with().entitySet(requestedEntitySet).build();

        final String collectionId = request.getRawBaseUri() + "/" + requestedEntitySet.getName();
        EntityCollectionSerializerOptions opts =
                EntityCollectionSerializerOptions.with().id(collectionId).contextURL(contextUrl).build();
        return serializer.entityCollection(serviceMetadata, requestedEntitySet.getEntityType(), entitySet, opts).getContent();
    }

    private EntityCollection filterEntities(UriInfo uriInfo, EdmEntitySet requestedEntitySet) {
        EntityCollection entitySet = getData(requestedEntitySet);
        if (uriInfo.getFilterOption() != null){
            LOGGER.info("Filter value = {}", uriInfo.getFilterOption().getText());

            String filter = uriInfo.getFilterOption().getText();
            Pattern regex = Pattern.compile("'(.*?)'");
            Matcher regexMatcher = regex.matcher(filter);
            regexMatcher.find();
            String predicate = regexMatcher.group(1).toLowerCase();
            LOGGER.info("Filter predicate = {}", predicate);
            EntityCollection filteredEntities = new EntityCollection();
            entitySet.getEntities().stream().filter(e -> {
                String id = (String) e.getProperty("ID").getValue();
                String name = (String) e.getProperty("name").getValue();
                return id.toLowerCase().contains(predicate) || name.toLowerCase().contains(predicate);
            }).collect(Collectors.toCollection(filteredEntities::getEntities));
            entitySet = filteredEntities;
            LOGGER.info("filtered entities = {}", entitySet.getEntities());
        }
        return entitySet;
    }

    private EntityCollection getData(EdmEntitySet edmEntitySet){
        if(CountryEdmProvider.ENTITY_SET_NAME.equals(edmEntitySet.getName())) {
           return countriesCollection;
        }
        return new EntityCollection();
    }

    protected static URI createId(String entitySetName, Object id) {
        try {
            return new URI(entitySetName + "(" + id + ")");
        } catch (URISyntaxException e) {
            throw new ODataRuntimeException("Unable to create id for entity: " + entitySetName, e);
        }
    }
}
