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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * Headers that java.net.http.HttpRequest.Builder refuses to set explicitly.
     * Filter them out before forwarding so we don't trip IllegalArgumentException.
     * See {@link HttpRequest.Builder#header(String, String)} restrictions.
     */
    private static final Set<String> JDK_HTTP_RESTRICTED_HEADERS = Set.of(
            "connection", "content-length", "date", "expect", "from",
            "host", "upgrade", "via", "warning"
    );

    private final RouteRegistry routeRegistry;
    private final RestClient restClient;

    /**
     * Dedicated HTTP client for streaming responses (SSE / chunked).
     * RestClient.exchange() consumes the body inside the lambda, which is
     * incompatible with returning a StreamingResponseBody that copies the
     * downstream InputStream after the controller method returns. The JDK
     * client gives us an HttpResponse&lt;InputStream&gt; whose body remains
     * readable until we close it.
     */
    private final HttpClient streamingHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Executes gateway proxy controller for `GatewayProxyController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.route`.
     * @param routeRegistry input consumed by GatewayProxyController.
     * @param restClientBuilder input consumed by GatewayProxyController.
     */
    public GatewayProxyController(RouteRegistry routeRegistry, RestClient.Builder restClientBuilder) {
        log.info(" Entering GatewayProxyController#GatewayProxyController");
        log.debug(" Entering GatewayProxyController#GatewayProxyController with debug context");
        this.routeRegistry = routeRegistry;
        this.restClient = restClientBuilder.build();
    }

    @RequestMapping("/**")
    public ResponseEntity<?> proxy(HttpServletRequest request,
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

        // SSE / streaming branch: when the client wants text/event-stream we
        // CANNOT call res.getBody().readAllBytes() — the body is a long-lived
        // stream that never EOFs during a session. Switch to the JDK HTTP
        // client and return a StreamingResponseBody so events are flushed
        // chunk-by-chunk to the client.
        if (acceptsEventStream(request)) {
            return proxyStreaming(request, targetUrl, method, body);
        }

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
     * True if the inbound request advertises text/event-stream in its Accept
     * header (Server-Sent Events).
     */
    private boolean acceptsEventStream(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("Accept");
        while (values != null && values.hasMoreElements()) {
            String value = values.nextElement();
            if (value == null) continue;
            for (String token : value.split(",")) {
                String trimmed = token.trim().toLowerCase(Locale.ROOT);
                if (trimmed.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Proxy a Server-Sent Events (or any chunked / long-lived) response by
     * keeping the downstream InputStream open and copying it to the client
     * via Spring MVC's {@link StreamingResponseBody}. Each chunk is flushed
     * so events reach the client immediately.
     */
    private ResponseEntity<StreamingResponseBody> proxyStreaming(HttpServletRequest request,
                                                                 String targetUrl,
                                                                 HttpMethod method,
                                                                 byte[] body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    // No request timeout: SSE responses are long-lived. The JDK
                    // client uses TCP keep-alive at the OS level.
                    .version(HttpClient.Version.HTTP_1_1);

            // Copy inbound headers, skipping hop-by-hop and JDK-restricted ones.
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String lower = name.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP_HEADERS.contains(lower) || JDK_HTTP_RESTRICTED_HEADERS.contains(lower)) {
                    continue;
                }
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    try {
                        builder.header(name, values.nextElement());
                    } catch (IllegalArgumentException ignored) {
                        // JDK refuses some headers; silently drop them.
                    }
                }
            }

            HttpRequest.BodyPublisher publisher = (body == null || body.length == 0)
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);
            builder.method(method.name(), publisher);

            HttpResponse<InputStream> downstream =
                    streamingHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            HttpHeaders responseHeaders = new HttpHeaders();
            for (Map.Entry<String, List<String>> entry : downstream.headers().map().entrySet()) {
                String name = entry.getKey();
                if (name == null || name.startsWith(":")) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP_HEADERS.contains(lower)
                        || GATEWAY_MANAGED_CORS_RESPONSE_HEADERS.contains(lower)) {
                    continue;
                }
                responseHeaders.addAll(name, entry.getValue());
            }
            // Disable any intermediary buffering and tell well-behaved
            // proxies (e.g., nginx) to flush chunks immediately.
            responseHeaders.setCacheControl("no-cache");
            responseHeaders.set("X-Accel-Buffering", "no");

            StreamingResponseBody streamingBody = outputStream -> {
                try (InputStream in = downstream.body()) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        // Flush after every chunk so SSE events surface
                        // immediately rather than at buffer-fill boundaries.
                        outputStream.flush();
                    }
                } catch (IOException ex) {
                    // Client disconnect (broken pipe) is normal for SSE; log at debug.
                    log.debug("Streaming proxy ended for {}: {}", targetUrl, ex.getMessage());
                }
            };

            return new ResponseEntity<>(streamingBody, responseHeaders,
                    HttpStatusCode.valueOf(downstream.statusCode()));

        } catch (Exception ex) {
            log.error("Streaming proxy error for {} {}: {}", method, targetUrl, ex.getMessage());
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            byte[] errorBody = ("{\"error\":\"Streaming gateway error: " + ex.getMessage() + "\"}").getBytes();
            StreamingResponseBody errorStream = out -> out.write(errorBody);
            return new ResponseEntity<>(errorStream, errorHeaders, HttpStatus.BAD_GATEWAY);
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
