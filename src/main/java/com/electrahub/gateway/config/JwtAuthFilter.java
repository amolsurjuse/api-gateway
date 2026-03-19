package com.electrahub.gateway.config;

import com.electrahub.gateway.security.JwtService;
import com.electrahub.gateway.security.TokenDenylistService;
import com.electrahub.gateway.security.TokenVersionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final JwtService jwtService;
    private final TokenDenylistService denylistService;
    private final TokenVersionService tokenVersionService;

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
                chain.doFilter(request, response);
                return;
            }

            if (denylistService.isDenied(parsed.jti())) {
                chain.doFilter(request, response);
                return;
            }

            UUID userId = UUID.fromString(parsed.uid());
            long currentVersion = tokenVersionService.getVersion(userId);
            if (parsed.tv() != currentVersion) {
                chain.doFilter(request, response);
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
            }

        } catch (Exception ignored) {
            // invalid token -> unauthenticated
        }

        chain.doFilter(request, response);
    }
}
