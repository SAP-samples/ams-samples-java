/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.filter;

import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.ValidationResult;
import com.sap.cloud.security.token.validation.validators.JwtX5tValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(filterName = "x5tValidator", urlPatterns = "/app/callback/*")
public class X5tValidatorFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(X5tValidatorFilter.class);
    JwtX5tValidator x5tValidator;

    public X5tValidatorFilter() {
        OAuth2ServiceConfiguration config = Environments.getCurrent().getIasConfiguration();
        x5tValidator = new JwtX5tValidator(config);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request instanceof HttpServletRequest httpRequest) {
            ValidationResult validationResult = x5tValidator.validate(Token.create(httpRequest.getHeader("Authorization")));
            if (validationResult.isValid()) {
                chain.doFilter(request, response);
            } else {
                sendUnauthenticatedResponse(response, validationResult.getErrorDescription());
            }
        }
    }

    private void sendUnauthenticatedResponse(ServletResponse response, String unauthenticatedReason) {
        if (response instanceof HttpServletResponse httpServletResponse) {
            try {
                LOGGER.error("UNAUTHENTICATED: {}", unauthenticatedReason);
                httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED); // 401
            } catch (IOException e) {
                LOGGER.error("Failed to send error response", e);
            }
        }
    }
}
