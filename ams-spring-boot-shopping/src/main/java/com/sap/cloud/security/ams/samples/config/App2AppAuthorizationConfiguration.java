package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.ams.api.ApiMapper;
import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.core.IasAuthorizationsProvider;
import com.sap.cloud.security.ams.dcn.PolicyName;
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
    private final Set<String> TECHNICAL_USER_APIS = Set.of("GetProducts");
    private final Set<String> PRINCIPAL_PROPAGATION_APIS = Set.of("GetProducts", "ExternalOrder");

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof IasAuthorizationsProvider<?> provider) {
            final ApiMapper TECHNICAL_USER_API_MAPPER = (String api, Principal principal) -> {
                if (TECHNICAL_USER_APIS.contains(api)) {
                    return Set.of(PolicyName.ofSegments("internal", api));
                } else {
                    return Collections.emptySet();
                }
            };
            final ApiMapper PRINCIPAL_PROPAGATION_API_MAPPER = (String api, Principal principal) -> {
                if (PRINCIPAL_PROPAGATION_APIS.contains(api)) {
                    return Set.of(PolicyName.ofSegments("internal", api));
                } else {
                    return Collections.emptySet();
                }
            };

            provider
                    .withApiMapper(TECHNICAL_USER_API_MAPPER, TECHNICAL_USER)
                    .withApiMapper(PRINCIPAL_PROPAGATION_API_MAPPER, FILTERED_PRINCIPAL_PROPAGATION);
        }
        return bean;
    }
}

