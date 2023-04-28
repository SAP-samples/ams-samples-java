/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples.filter;

import com.sap.cloud.security.ams.dcl.client.pdp.Attributes;
import com.sap.cloud.security.servlet.IasTokenAuthenticator;
import com.sap.cloud.security.servlet.TokenAuthenticationResult;
import com.sap.cloud.security.token.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

import static com.sap.cloud.security.token.TokenClaims.USER_NAME;

@WebFilter(filterName = "iasTokenValidator") // filter for any endpoint
public class IasSecurityFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IasSecurityFilter.class);
    private final IasTokenAuthenticator iasTokenAuthenticator;

    public IasSecurityFilter() {
        iasTokenAuthenticator = new IasTokenAuthenticator();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            TokenAuthenticationResult authenticationResult = iasTokenAuthenticator.validateRequest(request, response);
            if (authenticationResult.isAuthenticated()) {
                chain.doFilter(request, response);
            } else {
                sendUnauthenticatedResponse(response, authenticationResult.getUnauthenticatedReason());
            }
        } finally {
            SecurityContext.clearToken();
        }
    }

    private void sendUnauthenticatedResponse(ServletResponse response, String unauthenticatedReason) {
        if (response instanceof HttpServletResponse) {
            try {
                LOGGER.debug("UNAUTHENTICATED: {}", unauthenticatedReason);
                HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED); // 401
            } catch (IOException e) {
                LOGGER.error("Failed to send error response", e);
            }
        }
    }

    public static void sendUnauthorizedResponse(ServletResponse response, Attributes attributes) {
        sendUnauthorizedResponse(response, attributes, "user has a lack of permissions");
    }

    public static void sendUnauthorizedResponse(ServletResponse response, Attributes attributes, String message) {
        if (response instanceof HttpServletResponse) {
            try {
                String user = Objects.nonNull(SecurityContext.getAccessToken())
                        ? SecurityContext.getToken().getClaimAsString(USER_NAME) : "<Unknown>";
                HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                LOGGER.error("User {} is unauthorized with {}. Message: '{}'", user, attributes, message);
                httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "User " + user + " is unauthorized."); // 403
            } catch (IOException e) {
                LOGGER.error("Failed to send error response", e);
            }
        }
    }

    @Override
    public void destroy() {
        SecurityContext.clearToken();
    }
}
