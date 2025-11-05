package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.spring.config.IdentityServicesPropertySourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.*;

/**
 * Spring Security configuration for the AMS Shopping application.
 *
 * <p>
 * This configuration:
 * <ul>
 * <li>Configures route-level security using Spring Security's hasAuthority
 * checks</li>
 * <li>Integrates with AMS through the amsAuthenticationConverter</li>
 * <li>Uses Privilege constants with toAuthority() to check for
 * "action:resource" authorities</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@PropertySource(factory = IdentityServicesPropertySourceFactory.class, ignoreResourceNotFound = true, value = { "" })
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authz -> {
                    // Public endpoints - Spring Boot Actuator health check
                    authz.requestMatchers(GET, "/actuator/health").permitAll();

                    // Authenticated endpoints without authorization checks
                    authz.requestMatchers(GET, "/privileges").authenticated();

                    // Endpoints protected with AMS  method-level security
                    authz.requestMatchers(GET, "/products").authenticated();
                    authz.requestMatchers(GET, "/orders").authenticated();
                    authz.requestMatchers(POST, "/orders").authenticated();
                    authz.requestMatchers(DELETE, "/orders/**").authenticated();

                    // Showcases alternative endpoint protection via AMS route-level security
                    // authz.requestMatchers(GET, "/products").access(via.checkPrivilege(READ_PRODUCTS));
                    // authz.requestMatchers(GET, "/orders").access(via.precheckPrivilege(READ_ORDERS));
                    // authz.requestMatchers(POST, "/orders").access(via.precheckPrivilege(CREATE_ORDERS));
                    // authz.requestMatchers(DELETE, "/orders/**").access(via.checkPrivilege(DELETE_ORDERS));

                    // Deny all other requests
                    authz.anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
