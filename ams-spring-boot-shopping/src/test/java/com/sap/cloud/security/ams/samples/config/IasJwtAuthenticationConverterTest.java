package com.sap.cloud.security.ams.samples.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.spring.token.authentication.AuthenticationToken;
import com.sap.cloud.security.spring.token.authentication.JavaSecurityContextHolderStrategy;
import com.sap.cloud.security.token.SapIdToken;
import com.sap.cloud.security.token.SecurityContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;

class IasJwtAuthenticationConverterTest {

    private final IasJwtAuthenticationConverter converter = new IasJwtAuthenticationConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    void convertsJwtIntoSapAuthenticationToken() {
        Jwt jwt = testJwt(Map.of("sub", "alice", "app_tid", "tenant1"));

        var authentication = converter.convert(jwt);

        assertInstanceOf(AuthenticationToken.class, authentication);
        assertInstanceOf(SapIdToken.class, authentication.getPrincipal());
    }

    @Test
    void derivesAuthoritiesFromGroupsClaim() {
        Jwt jwt = testJwt(Map.of("sub", "alice", "groups", List.of("admin", "users")));

        var authentication = converter.convert(jwt);

        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertEquals(List.of("admin", "users"), authorities);
    }

    @Test
    void convertedAuthenticationEstablishesAmsPrincipal() {
        Jwt jwt = testJwt(Map.of("sub", "alice", "app_tid", "tenant1", "scim_id", "alice"));

        new JavaSecurityContextHolderStrategy()
                .setContext(new SecurityContextImpl(converter.convert(jwt)));

        assertInstanceOf(SapIdToken.class, SecurityContext.getToken());
        assertNotNull(Principal.fromSecurityContext());
    }

    private Jwt testJwt(Map<String, Object> claims) {
        try {
            String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
            String payload = base64Url(objectMapper.writeValueAsString(claims));
            String signature = base64Url("test-signature");
            String rawJwt = header + "." + payload + "." + signature;
            return new Jwt(rawJwt, Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "none"), claims);
        } catch (Exception e) {
            throw new AssertionError("Failed to build test JWT", e);
        }
    }

    private static String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes());
    }
}
