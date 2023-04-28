/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.samples.service.odata.CountryEdmProvider;
import com.sap.cloud.security.samples.service.odata.CountryEntityCollectionProcessor;
import com.sap.cloud.security.samples.service.odata.CountryEntityProcessor;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;

@WebServlet(urlPatterns = { ValueHelpServlet.ENDPOINT })
public class ValueHelpServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueHelpServlet.class);

    static final long serialVersionUID = 1L;
    static final String ENDPOINT = "/app/callback/value-help/*";

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        try {
            OData odata = OData.newInstance();
            ServiceMetadata serviceMetadata = odata.createServiceMetadata(new CountryEdmProvider(), new ArrayList<>());
            ODataHttpHandler handler = odata.createHandler(serviceMetadata);
            handler.register(new CountryEntityCollectionProcessor());
            handler.register(new CountryEntityProcessor());

            handler.process(request, response);

        } catch (RuntimeException e) {
            LOGGER.error("Server Error occurred ", e);
            throw new ServletException(e);
        }
    }
}
