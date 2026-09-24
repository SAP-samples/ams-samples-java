package com.sap.cloud.security.ams.samples.config;

import com.sap.cloud.security.spring.token.authentication.AuthenticationToken;
import com.sap.cloud.security.token.TokenClaims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts a validated IAS JWT into a SAP {@link AuthenticationToken}.
 *
 * <p>
 * This is required for the AMS integration: the cloud security library only copies
 * the token into its {@code SecurityContext} when the Spring Security principal is a
 * SAP {@code Token} (see {@code JavaSecurityContextHolderStrategy}). Only then can the
 * AMS library derive the current principal and evaluate the caller's policies.
 * Spring Security's default converter produces a plain {@code JwtAuthenticationToken}
 * whose principal is a plain {@code Jwt}, which leaves the AMS principal unresolved
 * and results in denied privilege checks (HTTP 403).
 * </p>
 *
 * <p>
 * The granted authorities are derived from the {@code groups} claim of the token.
 * Authorization decisions in this application, however, are made by AMS
 * (route-level checks via {@code AmsRouteSecurity} and method-level checks via
 * {@code @CheckPrivilege}/{@code @PrecheckPrivilege}).
 * </p>
 */
public class IasJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new AuthenticationToken(jwt, groupAuthorities(jwt));
    }

    private static List<GrantedAuthority> groupAuthorities(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList(TokenClaims.GROUPS);
        if (groups == null) {
            return Collections.emptyList();
        }
        return groups.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
