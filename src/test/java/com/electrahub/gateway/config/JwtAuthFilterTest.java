package com.electrahub.gateway.config;

import com.electrahub.gateway.security.JwtService;
import com.electrahub.gateway.security.TokenDenylistService;
import com.electrahub.gateway.security.TokenVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final TokenDenylistService denylistService = mock(TokenDenylistService.class);
    private final TokenVersionService tokenVersionService = mock(TokenVersionService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService, denylistService, tokenVersionService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns401WhenTokenIsExpired() throws Exception {
        String token = "expired-token";
        Date expiredAt = new Date(System.currentTimeMillis() - 60_000L);
        JwtService.ParsedToken parsedToken = new JwtService.ParsedToken(
                "user@example.com",
                "jti-expired",
                UUID.randomUUID().toString(),
                1L,
                expiredAt,
                List.of("USER")
        );

        when(jwtService.parseAndValidate(token)).thenReturn(parsedToken);
        when(jwtService.isNotExpired(expiredAt)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/session/api/v1/sessions/active");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void skipsJwtValidationForRefreshEvenWhenExpiredAuthorizationHeaderIsPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/api/auth/refresh");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parseAndValidate("expired-token");
    }

    @Test
    void returns401WhenTokenParsingFails() throws Exception {
        String token = "malformed-token";
        when(jwtService.parseAndValidate(token)).thenThrow(new RuntimeException("JWT parse failed"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/session/api/v1/sessions/active");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void allowsRequestWhenTokenIsValid() throws Exception {
        String token = "valid-token";
        UUID uid = UUID.randomUUID();
        Date expiresAt = new Date(System.currentTimeMillis() + 600_000L);
        JwtService.ParsedToken parsedToken = new JwtService.ParsedToken(
                "user@example.com",
                "jti-valid",
                uid.toString(),
                7L,
                expiresAt,
                List.of("USER")
        );

        when(jwtService.parseAndValidate(token)).thenReturn(parsedToken);
        when(jwtService.isNotExpired(expiresAt)).thenReturn(true);
        when(denylistService.isDenied("jti-valid")).thenReturn(false);
        when(tokenVersionService.getVersion(uid)).thenReturn(7L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/session/api/v1/sessions/active");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void allowsTermsPendingTokenVersionToReachTermsGate() throws Exception {
        String token = "terms-pending-token";
        UUID uid = UUID.randomUUID();
        Date expiresAt = new Date(System.currentTimeMillis() + 600_000L);
        JwtService.ParsedToken parsedToken = new JwtService.ParsedToken(
                "user@example.com",
                "jti-terms-pending",
                uid.toString(),
                0L,
                expiresAt,
                List.of("USER")
        );

        when(jwtService.parseAndValidate(token)).thenReturn(parsedToken);
        when(jwtService.isNotExpired(expiresAt)).thenReturn(true);
        when(denylistService.isDenied("jti-terms-pending")).thenReturn(false);
        when(tokenVersionService.getVersion(uid)).thenReturn(3L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/station/api/v1/stations");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(request.getAttribute("uid")).isEqualTo(uid.toString());
        assertThat(request.getAttribute("tv")).isEqualTo(0L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void returns401WhenNonZeroTokenVersionIsStale() throws Exception {
        String token = "stale-token";
        UUID uid = UUID.randomUUID();
        Date expiresAt = new Date(System.currentTimeMillis() + 600_000L);
        JwtService.ParsedToken parsedToken = new JwtService.ParsedToken(
                "user@example.com",
                "jti-stale",
                uid.toString(),
                2L,
                expiresAt,
                List.of("USER")
        );

        when(jwtService.parseAndValidate(token)).thenReturn(parsedToken);
        when(jwtService.isNotExpired(expiresAt)).thenReturn(true);
        when(denylistService.isDenied("jti-stale")).thenReturn(false);
        when(tokenVersionService.getVersion(uid)).thenReturn(3L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/session/api/v1/sessions/history");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
