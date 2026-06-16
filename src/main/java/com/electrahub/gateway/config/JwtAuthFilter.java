package com.electrahub.gateway.config;

import com.electrahub.gateway.security.JwtService;
import com.electrahub.gateway.security.TokenDenylistService;
import com.electrahub.gateway.security.TokenVersionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final long TERMS_PENDING_TOKEN_VERSION = 0L;

    private final JwtService jwtService;
    private final TokenDenylistService denylistService;
    private final TokenVersionService tokenVersionService;
    private static final String WWW_AUTHENTICATE_BEARER_INVALID_TOKEN = "Bearer error=\"invalid_token\"";

    public JwtAuthFilter(
            JwtService jwtService,
            TokenDenylistService denylistService,
            TokenVersionService tokenVersionService
    ) {
        this.jwtService = jwtService;
        this.denylistService = denylistService;
        this.tokenVersionService = tokenVersionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            JwtService.ParsedToken parsed = jwtService.parseAndValidate(token);
            if (!jwtService.isNotExpired(parsed.exp())) {
                log.debug("JWT rejected: token expired for subject={} path={}",
                        parsed.subjectEmail(), request.getRequestURI());
                rejectUnauthorized(response);
                return;
            }

            if (isDenied(parsed.jti(), request.getRequestURI())) {
                log.debug("JWT rejected: denylisted jti={} path={}",
                        parsed.jti(), request.getRequestURI());
                rejectUnauthorized(response);
                return;
            }

            UUID userId = UUID.fromString(parsed.uid());
            long currentVersion = resolveCurrentTokenVersion(userId, parsed.tv(), request.getRequestURI());
            if (!isTermsPendingToken(parsed.tv()) && parsed.tv() != currentVersion) {
                log.debug("JWT rejected: tokenVersion mismatch uid={} tokenTv={} currentTv={} path={}",
                        userId, parsed.tv(), currentVersion, request.getRequestURI());
                rejectUnauthorized(response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var authorities = parsed.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(parsed.subjectEmail(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                request.setAttribute("uid", parsed.uid());
                request.setAttribute("jti", parsed.jti());
                request.setAttribute("exp", parsed.exp());
                request.setAttribute("tv", parsed.tv());

                log.debug("JWT accepted: uid={} subject={} roles={} path={}",
                        parsed.uid(), parsed.subjectEmail(), parsed.roles(), request.getRequestURI());
            }

        } catch (Exception ex) {
            log.warn("JWT processing failed for path={} reason={}",
                    request.getRequestURI(), ex.getMessage());
            rejectUnauthorized(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_BEARER_INVALID_TOKEN);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    /**
     * Executes is denied for `JwtAuthFilter`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param jti input consumed by isDenied.
     * @param path input consumed by isDenied.
     * @return result produced by isDenied.
     */
    private boolean isDenied(String jti, String path) {
        log.info(" Entering JwtAuthFilter#isDenied");
        log.debug(" Entering JwtAuthFilter#isDenied with debug context");
        try {
            return denylistService.isDenied(jti);
        } catch (RuntimeException ex) {
            // Fail open for denylist/version checks when Redis is unavailable.
            // JWT signature + expiration + issuer validation still apply.
            log.warn("Redis denylist check unavailable; proceeding with JWT-only validation path={} reason={}",
                    path, ex.getMessage());
            return false;
        }
    }

    /**
     * Executes resolve current token version for `JwtAuthFilter`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param userId input consumed by resolveCurrentTokenVersion.
     * @param tokenVersionFromJwt input consumed by resolveCurrentTokenVersion.
     * @param path input consumed by resolveCurrentTokenVersion.
     * @return result produced by resolveCurrentTokenVersion.
     */
    private long resolveCurrentTokenVersion(UUID userId, long tokenVersionFromJwt, String path) {
        try {
            return tokenVersionService.getVersion(userId);
        } catch (RuntimeException ex) {
            // Preserve availability if Redis is down by trusting token version claim.
            log.warn("Redis token-version check unavailable; using JWT token version path={} uid={} reason={}",
                    path, userId, ex.getMessage());
            return tokenVersionFromJwt;
        }
    }

    private boolean isTermsPendingToken(long tokenVersion) {
        return tokenVersion == TERMS_PENDING_TOKEN_VERSION;
    }
}
