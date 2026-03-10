package com.sap.cloud.security.ams.samples.config;

import java.time.Instant;
import java.util.*;

import com.sap.cloud.security.token.*;
import com.sap.cloud.security.xsuaa.jwt.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;

@TestConfiguration
public class TestSecurityConfiguration {

    private final Base64JwtDecoder base64JwtDecoder = Base64JwtDecoder.getInstance();

    /**
     * Override the production JwtDecoder with a test version that decodes
     * JWTs without validation and sets up the SecurityContext of the cloud security
     * library which is used by IdentityServiceAuthProvider.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                DecodedJwt decodedJwt = base64JwtDecoder.decode(token);
                SapIdToken sapIdToken = new SapIdToken(decodedJwt);
                SecurityContext.setToken(sapIdToken);

                Map<String, Object> headers = sapIdToken.getHeaders();
                Map<String, Object> claims = sapIdToken.getClaims();
                Instant issuedAt = claims.containsKey("iat")
                        ? Instant.ofEpochSecond(((Number) claims.get("iat")).longValue())
                        : Instant.now();
                Instant expiresAt = Optional.ofNullable(sapIdToken.getExpiration())
                        .orElse(Instant.now().plusSeconds(3600));

                return new Jwt(token, issuedAt, expiresAt, headers, claims);
            } catch (Exception e) {
                throw new JwtException("Failed to decode test JWT", e);
            }
        };
    }
}
