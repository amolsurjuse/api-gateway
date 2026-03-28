package com.electrahub.gateway.route;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Transparent reverse proxy controller.
 *
 * Matches any incoming request, extracts the first path segment as the service prefix,
 * resolves the backend URL from RouteRegistry, and forwards the full request
 * (method, headers, query string, body) to the target service.
 *
 * The service prefix is stripped from the forwarded path:
 *   GET /user/api/v1/users?page=0  →  GET http://user-service:8082/api/v1/users?page=0
 */
@RestController
public class GatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyController.class);

    /**
     * Headers that must NOT be forwarded to the backend (hop-by-hop or proxy-controlled).
     */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding",
            "upgrade", "content-length"
    );
    /**
     * CORS headers are managed centrally at API Gateway SecurityConfig.
     * Strip backend CORS headers to avoid duplicate Access-Control-* values.
     */
    private static final Set<String> GATEWAY_MANAGED_CORS_RESPONSE_HEADERS = Set.of(
            "access-control-allow-origin",
            "access-control-allow-methods",
            "access-control-allow-headers",
            "access-control-expose-headers",
            "access-control-allow-credentials",
            "access-control-max-age"
    );

    private final RouteRegistry routeRegistry;
    private final RestClient restClient;

    /**
     * Executes gateway proxy controller for `GatewayProxyController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.route`.
     * @param routeRegistry input consumed by GatewayProxyController.
     * @param restClientBuilder input consumed by GatewayProxyController.
     */
    public GatewayProxyController(RouteRegistry routeRegistry, RestClient.Builder restClientBuilder) {
        log.info("CODEx_ENTRY_LOG: Entering GatewayProxyController#GatewayProxyController");
        log.debug("CODEx_ENTRY_LOG: Entering GatewayProxyController#GatewayProxyController with debug context");
        this.routeRegistry = routeRegistry;
        this.restClient = restClientBuilder.build();
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        /**
                                         * Executes request body for `GatewayProxyController`.
                                         *
                                         * <p>Detailed behavior: follows the current implementation path and
                                         * enforces component-specific rules in `com.electrahub.gateway.route`.
                                         * @param body input consumed by RequestBody.
                                         * @return result produced by RequestBody.
                                         */
                                        @RequestBody(required = false) byte[] body) {

        String path = request.getRequestURI();
        String query = request.getQueryString();

        // Extract the service prefix (first path segment)
        String prefix = extractPrefix(path);
        if (prefix == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"No route matched\"}".getBytes());
        }

        String backendUrl = routeRegistry.resolve(prefix);
        if (backendUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("{\"error\":\"Unknown service: " + prefix + "\"}").getBytes());
        }

        // Strip the prefix from the path: /user/api/v1/users → /api/v1/users
        String downstreamPath = path.substring(prefix.length() + 1); // +1 for leading /
        String targetUrl = backendUrl + downstreamPath;
        if (query != null && !query.isEmpty()) {
            targetUrl += "?" + query;
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        log.debug("Proxying {} {} → {}", method, path, targetUrl);

        try {
            var spec = restClient.method(method)
                    .uri(URI.create(targetUrl))
                    .headers(headers -> copyHeaders(request, headers));

            if (body != null && body.length > 0) {
                String contentType = request.getContentType();
                if (contentType != null) {
                    spec.contentType(MediaType.parseMediaType(contentType));
                }
                spec.body(body);
            }

            return spec.exchange((req, res) -> {
                byte[] responseBody = res.getBody().readAllBytes();

                HttpHeaders responseHeaders = new HttpHeaders();
                res.getHeaders().forEach((name, values) -> {
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    boolean isPseudoHeader = name.startsWith(":");
                    if (!isPseudoHeader
                            && !HOP_BY_HOP_HEADERS.contains(lowerName)
                            && !GATEWAY_MANAGED_CORS_RESPONSE_HEADERS.contains(lowerName)) {
                        responseHeaders.addAll(name, values);
                    }
                });

                return new ResponseEntity<>(responseBody, responseHeaders, HttpStatusCode.valueOf(res.getStatusCode().value()));
            });

        } catch (RestClientResponseException ex) {
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(ex.getResponseBodyAsByteArray(), errorHeaders,
                    HttpStatusCode.valueOf(ex.getStatusCode().value()));
        } catch (Exception ex) {
            log.error("Proxy error for {} {}: {}", method, targetUrl, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"error\":\"Gateway error: " + ex.getMessage() + "\"}").getBytes());
        }
    }

    /**
     * Extract the first path segment as the route prefix.
     * "/user/api/v1/users" → "user"
     */
    private String extractPrefix(String path) {
        if (path == null || path.length() < 2) return null;

        // Remove leading slash
        String trimmed = path.substring(1);
        int slashIndex = trimmed.indexOf('/');
        return slashIndex > 0 ? trimmed.substring(0, slashIndex) : trimmed;
    }

    /**
     * Copy request headers to the downstream request, filtering out hop-by-hop headers.
     */
    private void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    headers.add(name, values.nextElement());
                }
            }
        }
    }
}
