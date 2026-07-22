package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(HttpClientConfig.GatewayHttpClientProperties.class)
public class HttpClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientConfig.class);


    /**
     * Executes rest client builder for `HttpClientConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by restClientBuilder.
     */
    @Bean
    RestClient.Builder restClientBuilder(GatewayHttpClientProperties properties) {
        LOGGER.info(" Entering HttpClientConfig#restClientBuilder");
        LOGGER.debug(" Entering HttpClientConfig#restClientBuilder with debug context");
        JdkClientHttpRequestFactory requestFactory = requestFactory(properties.connectTimeout(), properties.readTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    /**
     * Local LLM inference can take longer than a normal service request. Keep
     * that allowance isolated to the AI route so stalled business APIs still
     * fail fast under the standard gateway timeout.
     */
    @Bean("aiRestClient")
    RestClient aiRestClient(GatewayHttpClientProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.aiReadTimeout()))
                .build();
    }

    private JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    @ConfigurationProperties(prefix = "gateway.http-client")
    public record GatewayHttpClientProperties(
            Duration connectTimeout,
            Duration readTimeout,
            Duration aiReadTimeout,
            Duration streamingConnectTimeout
    ) {
        public GatewayHttpClientProperties {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(12) : readTimeout;
            aiReadTimeout = aiReadTimeout == null ? Duration.ofSeconds(55) : aiReadTimeout;
            streamingConnectTimeout = streamingConnectTimeout == null ? Duration.ofSeconds(5) : streamingConnectTimeout;
        }
    }
}
