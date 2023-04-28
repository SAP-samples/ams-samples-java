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

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(filterName = "x5tValidator")
public class X5tValidatorFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(X5tValidatorFilter.class);
    JwtX5tValidator x5tValidator;

    public X5tValidatorFilter() {
        OAuth2ServiceConfiguration config = Environments.getCurrent().getIasConfiguration();
        x5tValidator = new JwtX5tValidator(config);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = ((HttpServletRequest) request);
            ValidationResult validationResult = x5tValidator.validate(Token.create(httpRequest.getHeader("Authorization")));
            if (validationResult.isValid()) {
                chain.doFilter(request, response);
            } else {
                sendUnauthenticatedResponse(response, validationResult.getErrorDescription());
            }
        }
    }

    private void sendUnauthenticatedResponse(ServletResponse response, String unauthenticatedReason) {
        if (response instanceof HttpServletResponse) {
            try {
                LOGGER.error("UNAUTHENTICATED: {}", unauthenticatedReason);
                HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED); // 401
            } catch (IOException e) {
                LOGGER.error("Failed to send error response", e);
            }
        }
    }
}
