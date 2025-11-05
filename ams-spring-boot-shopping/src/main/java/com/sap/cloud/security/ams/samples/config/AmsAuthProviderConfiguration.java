package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.ams.api.ApiMapper;
import com.sap.cloud.security.ams.api.AuthProvider;
import com.sap.cloud.security.ams.api.AuthorizationManagementService;
import com.sap.cloud.security.ams.core.IdentityServiceAuthProvider;
import com.sap.cloud.security.token.SapIdToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.sap.cloud.security.ams.api.App2AppFlow.RESTRICTED_PRINCIPAL_PROPAGATION;
import static com.sap.cloud.security.ams.api.App2AppFlow.TECHNICAL_USER;

/**
 * AMS AuthProvider configuration for the Shopping application.
 *
 * <p>
 * This configuration defines the custom AuthProvider with API-to-Policy mappings
 * for both technical user and principal propagation flows.
 * </p>
 */
@Configuration
public class AmsAuthProviderConfiguration {

    @Bean
    public AuthProvider authProvider(@Lazy AuthorizationManagementService ams) {
        final ApiMapper PRINCIPAL_PROPAGATION_API_MAPPER = getPrincipalPropagationApiMapper();
        final ApiMapper TECHNICAL_USER_API_MAPPER = getTechnicalUserApiMapper();

        return new IdentityServiceAuthProvider(ams)
                .withApiMapper(TECHNICAL_USER_API_MAPPER, TECHNICAL_USER)
                .withApiMapper(PRINCIPAL_PROPAGATION_API_MAPPER, RESTRICTED_PRINCIPAL_PROPAGATION);
    }

    /**
     * This method showcases the definition of an ApiMapper based on an explicit Map data structure.
     *
     * @return an ApiMapper that maps principal propagation APIs to internal policies with the same name in the "internal" DCL package.
     */
    private ApiMapper getPrincipalPropagationApiMapper() {
        final Map<String, Set<String>> PRINCIPAL_PROPAGATION_API_TO_POLICY = Map.of(
                "GetProducts", Set.of("internal.GetProducts"),
                "ExternalOrder", Set.of("internal.ExternalOrder"));
        return ApiMapper.ofMap(PRINCIPAL_PROPAGATION_API_TO_POLICY);
    }

    /**
     * This method showcases the definition of an ApiMapper with a lambda implementation.
     *
     * @return an ApiMapper that maps technical user APIs to internal policies with the same name in the "internal" DCL package.
     */
    private static ApiMapper getTechnicalUserApiMapper() {
        final Set<String> TECHNICAL_USER_APIS = Set.of("GetProducts");
        return (String apiName, SapIdToken token) -> {
            if (TECHNICAL_USER_APIS.contains(apiName)) {
                return Set.of(String.format("internal.%s", apiName));
            } else {
                return Collections.emptySet();
            }
        };
    }
}

