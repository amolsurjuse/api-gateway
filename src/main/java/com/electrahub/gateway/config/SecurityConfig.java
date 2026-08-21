package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.gateway.security.ApiPolicyAuthorizationManager;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
        LOGGER.info(" Entering SecurityConfig#SecurityConfig");
        LOGGER.debug(" Entering SecurityConfig#SecurityConfig with debug context");
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
            AiRateLimitFilter aiRateLimitFilter,
            TermsAcceptanceGateFilter termsAcceptanceGateFilter,
            ApiPolicyAuthorizationManager apiPolicyAuthorizationManager
    ) throws Exception {

        http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value()))
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/internal/rbac/cache/**").permitAll()
                        // Monitoring must not depend on a mutable remote RBAC snapshot.
                        // Actuator exposure remains limited by management endpoint config.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus"
                        ).permitAll()
                        // OCPI uses its own opaque Token scheme. The OCPI service is the
                        // fail-closed authorization boundary for every protocol endpoint.
                        .requestMatchers("/ocpi/**").permitAll()
                        .anyRequest().access(apiPolicyAuthorizationManager)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(aiRateLimitFilter, JwtAuthFilter.class)
                .addFilterAfter(termsAcceptanceGateFilter, JwtAuthFilter.class);

        return http.build();
    }
}
