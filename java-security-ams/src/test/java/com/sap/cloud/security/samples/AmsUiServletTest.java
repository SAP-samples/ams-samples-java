package com.sap.cloud.security.samples;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled
class AmsUiServletTest {

    private static AmsUiServlet servlet;
    private static HttpServletRequest request;
    private static HttpServletResponse response;

    @BeforeAll
    static void beforeAll() {
        servlet = new AmsUiServlet();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void doGet_isMt() throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        when(response.getWriter()).thenReturn(writer);
        when(request.getHeader("x-forwarded-host")).thenReturn("subscriber-subdomain-ar-user.cf.eu.sap.com");

        servlet.doGet(request,response);
        assertEquals("https://subscriber-tenant-id--subscriber-tenant-name.authorization.cf.sap.com", stringWriter.toString());
    }

    @Test
    void doGet_isNotMt() throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        when(response.getWriter()).thenReturn(writer);
        when(request.getHeader("x-forwarded-host")).thenReturn("");

        servlet.doGet(request,response);
        assertEquals("https://provider-tenant-id--provider-tenant-name.authorization.cf.sap.com", stringWriter.toString());
    }
}