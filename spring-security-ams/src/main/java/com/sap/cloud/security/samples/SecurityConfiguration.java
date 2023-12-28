/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.spring.config.IdentityServicesPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.springframework.http.HttpMethod.GET;

@Configuration
@PropertySource(factory = IdentityServicesPropertySourceFactory.class, ignoreResourceNotFound = true, value = { "" })
public class SecurityConfiguration {

    @Autowired
    Converter<Jwt, AbstractAuthenticationToken> amsAuthenticationConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityExpressionHandler<RequestAuthorizationContext> amsHttpExpressionHandler) throws Exception {
        WebExpressionAuthorizationManager hasBaseAuthority = new WebExpressionAuthorizationManager("hasBaseAuthority('read', 'salesOrders')");
        hasBaseAuthority.setExpressionHandler(amsHttpExpressionHandler);
        http.authorizeHttpRequests(authz -> {
                    authz.requestMatchers(GET, "/health", "/", "/uiurl")
                            .permitAll();
                    authz.requestMatchers(GET, "/salesOrders/**")
                            .access(hasBaseAuthority);
                    authz.requestMatchers(GET, "/authorized")
                            .hasAuthority("view").anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(amsAuthenticationConverter)));
        return http.build();
    }
}
