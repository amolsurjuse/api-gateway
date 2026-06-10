package com.electrahub.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void corsConfigurationSourceAllowsConfiguredCloudflareOrigins() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOriginPatterns(List.of(
                "https://admin-dev.electrahub.net",
                "https://driver-dev.electrahub.net"
        ));
        SecurityConfig config = new SecurityConfig(corsProperties);

        var source = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        var cors = source.getCorsConfigurations().get("/**");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns())
                .containsExactly("https://admin-dev.electrahub.net", "https://driver-dev.electrahub.net");
        assertThat(cors.getAllowedMethods()).contains("POST", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).contains("*");
        assertThat(cors.getAllowCredentials()).isTrue();
    }
}
