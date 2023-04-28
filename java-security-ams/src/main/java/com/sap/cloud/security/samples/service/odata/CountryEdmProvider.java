/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.service.odata;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CountryEdmProvider extends CsdlAbstractEdmProvider {

    public static final String NAMESPACE = "com.sap.cloud.security";

    public static final String CONTAINER_NAME = "CountryContainer";
    public static final FullQualifiedName CONTAINER_FQN = new FullQualifiedName(NAMESPACE, CONTAINER_NAME);

    public static final String ENTITY_TYPE_NAME = "Country";
    public static final FullQualifiedName ENTITY_TYPE_FQN = new FullQualifiedName(NAMESPACE, ENTITY_TYPE_NAME);

    public static final String ENTITY_SET_NAME = "Country";

    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) {
        if(entityTypeName.equals(ENTITY_TYPE_FQN)){
            CsdlProperty id = new CsdlProperty().setName("ID").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
            CsdlProperty name = new CsdlProperty().setName("name").setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());

            CsdlPropertyRef propertyRef = new CsdlPropertyRef();
            propertyRef.setName("ID");

            CsdlEntityType entityType = new CsdlEntityType();
            entityType.setName(ENTITY_TYPE_NAME);
            entityType.setProperties(Arrays.asList(id, name));
            entityType.setKey(Collections.singletonList(propertyRef));

            return entityType;
        }
        return null;
    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) {
        if(entityContainer.equals(CONTAINER_FQN) && entitySetName.equals(ENTITY_SET_NAME)){
            CsdlEntitySet entitySet = new CsdlEntitySet();
            entitySet.setName(ENTITY_SET_NAME);
            entitySet.setType(ENTITY_TYPE_FQN);
            return entitySet;
        }
        return null;
    }

    @Override
    public CsdlEntityContainer getEntityContainer() {
        List<CsdlEntitySet> entitySets = new ArrayList<>();
        entitySets.add(getEntitySet(CONTAINER_FQN, ENTITY_SET_NAME));

        CsdlEntityContainer entityContainer = new CsdlEntityContainer();
        entityContainer.setName(CONTAINER_NAME);
        entityContainer.setEntitySets(entitySets);

        return entityContainer;
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) {
        if(entityContainerName == null || entityContainerName.equals(CONTAINER_FQN)){
            CsdlEntityContainerInfo entityContainerInfo = new CsdlEntityContainerInfo();
            entityContainerInfo.setContainerName(CONTAINER_FQN);
            return entityContainerInfo;
        }
        return null;
    }

    @Override
    public List<CsdlSchema> getSchemas() {
        CsdlSchema schema = new CsdlSchema();
        schema.setNamespace(NAMESPACE);

        List<CsdlEntityType> entityTypes = new ArrayList<>();
        entityTypes.add(getEntityType(ENTITY_TYPE_FQN));
        schema.setEntityTypes(entityTypes);

        schema.setEntityContainer(getEntityContainer());

        return Collections.singletonList(schema);
    }

}
