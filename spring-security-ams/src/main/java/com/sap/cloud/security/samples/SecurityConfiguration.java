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
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.GET;

@Configuration
@PropertySource(factory = IdentityServicesPropertySourceFactory.class, ignoreResourceNotFound = true, value = { "" })
public class SecurityConfiguration {

    @Autowired
    SecurityExpressionHandler<FilterInvocation> amsWebExpressionHandler;

    @Autowired
    Converter<Jwt, AbstractAuthenticationToken> amsAuthenticationConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .expressionHandler(amsWebExpressionHandler)
                .antMatchers(GET, "/health", "/", "/uiurl")
                    .permitAll()
                .antMatchers(GET, "/salesOrders/**")
                    .access("hasBaseAuthority('read', 'salesOrders')")
                .antMatchers(GET, "/authorized")
                    .hasAuthority("view").anyRequest().authenticated()
                .and()
                    .oauth2ResourceServer()
                    .jwt().jwtAuthenticationConverter(amsAuthenticationConverter);
        return http.build();
    }
}
