package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.gateway.security.ApiPolicyAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);


    private final CorsProperties corsProperties;

    /**
     * Executes security config for `SecurityConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param corsProperties input consumed by SecurityConfig.
     */
    public SecurityConfig(CorsProperties corsProperties) {
        LOGGER.debug("Initializing gateway security configuration");
        this.corsProperties = corsProperties;
    }

    /**
     * Executes cors configuration source for `SecurityConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by corsConfigurationSource.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        LOGGER.debug("Building CORS configuration with {} allowed origin pattern(s)",
                corsProperties.getAllowedOriginPatterns() == null ? 0 : corsProperties.getAllowedOriginPatterns().size());
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        // Allow framework-specific headers (e.g. X-XSRF-TOKEN) without repeated gateway redeploys.
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            TermsAcceptanceGateFilter termsAcceptanceGateFilter,
            ApiPolicyAuthorizationManager apiPolicyAuthorizationManager
    ) {

        LOGGER.debug("Configuring stateless security filter chain with JWT auth, terms gate, and RBAC authorization");

        http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/rbac/cache/**").permitAll()
                        .anyRequest().access(apiPolicyAuthorizationManager)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(termsAcceptanceGateFilter, JwtAuthFilter.class);

        return http.build();
    }
}
