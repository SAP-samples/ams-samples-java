package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.ams.spring.AmsRouteSecurity;
import com.sap.cloud.security.spring.config.IdentityServicesPropertySourceFactory;
import com.sap.cloud.security.spring.token.authentication.AuthenticationToken;
import com.sap.cloud.security.token.TokenClaims;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.sap.cloud.security.ams.samples.config.Privileges.*;
import static org.springframework.http.HttpMethod.*;

/**
 * Spring Security configuration for the AMS Shopping application.
 *
 * <p>
 * This configuration:
 * <ul>
 * <li>Configures route-level security using AMS route-level checks
 * ({@code AmsRouteSecurity}) in addition to standard Spring Security rules</li>
 * <li>Wraps every validated IAS JWT into a SAP {@link AuthenticationToken}: the cloud
 * security library only copies the token into its {@code SecurityContext} when the
 * Spring Security principal is a SAP {@code Token} (see
 * {@code JavaSecurityContextHolderStrategy}). Only then can the AMS library derive the
 * current principal and evaluate the caller's policies. Spring Security's default
 * converter produces a plain {@code Jwt} principal and would leave the AMS principal
 * unresolved, denying all privilege checks with HTTP 403.</li>
 * <li>Uses Privilege constants with toAuthority() to check for
 * "action:resource" authorities</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@PropertySource(factory = IdentityServicesPropertySourceFactory.class, ignoreResourceNotFound = true, value = {""})
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AmsRouteSecurity via) throws Exception {
        http.authorizeHttpRequests(authz -> {
                    // Public endpoints - health checks
                    authz.requestMatchers(GET, "/actuator/health").permitAll();
                    authz.requestMatchers(GET, "/health").permitAll();

                    // Authenticated endpoints without authorization checks
                    authz.requestMatchers(GET, "/privileges").authenticated();

                    // CHOOSE ONE idiom below (route-level authorization + service-level filtering vs. full service-level authorization

                    // Idiom 1 (Full service-level authorization): these endpoints are protected with AMS method-level security
                    authz.requestMatchers(GET, "/products").authenticated();
                    authz.requestMatchers(GET, "/orders").authenticated();
                    authz.requestMatchers(POST, "/orders").authenticated();
                    authz.requestMatchers(DELETE, "/orders/**").authenticated();

                    // Idiom 2 (route-level authorization + service-level filtering) Showcases alternative endpoint protection via AMS route-level security (+ service-level filtering)
                    authz.requestMatchers(GET, "/products").access(via.checkPrivilege(READ_PRODUCTS));
                    authz.requestMatchers(GET, "/orders").access(via.precheckPrivilege(READ_ORDERS));
                    authz.requestMatchers(POST, "/orders").access(via.precheckPrivilege(CREATE_ORDERS));
                    authz.requestMatchers(DELETE, "/orders/**").access(via.checkPrivilege(DELETE_ORDERS));

                    // Deny all other requests
                    authz.anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                j -> new AuthenticationToken(j, groupAuthorities(j)))));

        return http.build();
    }

    /**
     * Maps the {@code groups} claim of the token to Spring Security authorities.
     * The authorities are informational: authorization decisions in this
     * application are made by AMS (route-level checks via {@code AmsRouteSecurity}
     * and method-level checks via {@code @CheckPrivilege}/{@code @PrecheckPrivilege}).
     */
    static List<GrantedAuthority> groupAuthorities(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList(TokenClaims.GROUPS);
        if (groups == null) {
            return Collections.emptyList();
        }
        return groups.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
