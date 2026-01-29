package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.ams.api.ApiMapper;
import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.core.IasAuthorizationsProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.sap.cloud.security.ams.api.App2AppFlow.FILTERED_PRINCIPAL_PROPAGATION;
import static com.sap.cloud.security.ams.api.App2AppFlow.TECHNICAL_USER;

/**
 * AMS AuthProvider configuration for the Shopping application.
 *
 * <p>
 * This configuration customizes the default AuthorizationsProvider bean created by
 * AmsAutoConfiguration by adding API-to-Policy mappings for both technical user and
 * principal propagation flows.
 * </p>
 */
@Configuration
public class App2AppAuthorizationConfiguration implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof IasAuthorizationsProvider<?> provider) {
            final ApiMapper PRINCIPAL_PROPAGATION_API_MAPPER = getPrincipalPropagationApiMapper();
            final ApiMapper TECHNICAL_USER_API_MAPPER = getTechnicalUserApiMapper();

            provider.withApiMapper(TECHNICAL_USER_API_MAPPER, TECHNICAL_USER)
                    .withApiMapper(PRINCIPAL_PROPAGATION_API_MAPPER, FILTERED_PRINCIPAL_PROPAGATION);
        }
        return bean;
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
        return (String api, Principal principal) -> {
            if (TECHNICAL_USER_APIS.contains(api)) {
                return Set.of(String.format("internal.%s", api));
            } else {
                return Collections.emptySet();
            }
        };
    }
}

