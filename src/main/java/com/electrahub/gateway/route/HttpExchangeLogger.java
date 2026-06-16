package com.electrahub.gateway.route;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
public class HttpExchangeLogger {

    private static final Logger log = LoggerFactory.getLogger(HttpExchangeLogger.class);

    private static final String MASK = "***";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-api-key",
            "x-auth-token",
            "x-csrf-token",
            "x-internal-api-key"
    );

    private static final Set<String> SENSITIVE_QUERY_PARAMS = Set.of(
            "access_token",
            "authorization",
            "client_secret",
            "code",
            "id_token",
            "password",
            "refresh_token",
            "secret",
            "token"
    );

    private static final Pattern JSON_SECRET_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:accessToken|authorization|cardNumber|clientSecret|cvv|idToken|password|refreshToken|secret|token)\"\\s*:\\s*\")([^\"]*)(\")"
    );

    private final boolean enabled;
    private final boolean includeHeaders;
    private final boolean includeBodies;
    private final boolean fullRequestResponseLogging;
    private final int maxBodyLength;

    @Autowired
    public HttpExchangeLogger(
            @Value("${app.http-logging.enabled:true}") boolean enabled,
            @Value("${app.http-logging.include-headers:true}") boolean includeHeaders,
            @Value("${app.http-logging.include-bodies:true}") boolean includeBodies,
            @Value("${app.http-logging.full-request-response:false}") boolean fullRequestResponseLogging,
            @Value("${app.http-logging.max-body-length:4096}") int maxBodyLength
    ) {
        this.enabled = enabled;
        this.includeHeaders = includeHeaders;
        this.includeBodies = includeBodies;
        this.fullRequestResponseLogging = fullRequestResponseLogging;
        this.maxBodyLength = Math.max(0, maxBodyLength);
    }

    HttpExchangeLogger(boolean enabled, boolean includeHeaders, boolean includeBodies, int maxBodyLength) {
        this(enabled, includeHeaders, includeBodies, false, maxBodyLength);
    }


    public long started() {
        return System.nanoTime();
    }

    public void logRequest(HttpServletRequest request, HttpMethod method, String path, String targetUrl, byte[] body) {
//        if (!enabled) {
//            return;
//        }

        log.info("GW_REQUEST method={} path={} target={} headers={} body={}",
                method,
                inboundUrl(request, path),
                sanitizeUrl(targetUrl),
                shouldIncludeHeaders() ? sanitizeRequestHeaders(request) : "<disabled>",
                shouldIncludeBodies() ? summarizeBody(body, request.getContentType()) : "<disabled>");
    }

    public void logResponse(
            HttpServletRequest request,
            HttpMethod method,
            String path,
            String targetUrl,
            int status,
            HttpHeaders headers,
            byte[] body,
            long startedAtNanos
    ) {
//        if (!enabled) {
//            return;
//        }

        log.info("GW_RESPONSE method={} path={} target={} status={} durationMs={} headers={} body={}",
                method,
                inboundUrl(request, path),
                sanitizeUrl(targetUrl),
                status,
                durationMillis(startedAtNanos),
                shouldIncludeHeaders() ? sanitizeHeaders(headers) : "<disabled>",
                shouldIncludeBodies() ? summarizeBody(body, firstHeader(headers, HttpHeaders.CONTENT_TYPE)) : "<disabled>");
    }

    public void logStreamingResponseStarted(
            HttpServletRequest request,
            HttpMethod method,
            String path,
            String targetUrl,
            int status,
            HttpHeaders headers,
            long startedAtNanos
    ) {
//        if (!enabled) {
//            return;
//        }

        log.info("GW_STREAM_RESPONSE_STARTED method={} path={} target={} status={} durationMs={} headers={} body=<streaming>",
                method,
                inboundUrl(request, path),
                sanitizeUrl(targetUrl),
                status,
                durationMillis(startedAtNanos),
                shouldIncludeHeaders() ? sanitizeHeaders(headers) : "<disabled>");
    }

    public void logFailure(
            HttpServletRequest request,
            HttpMethod method,
            String path,
            String targetUrl,
            Exception exception,
            long startedAtNanos
    ) {
//        if (!enabled) {
//            return;
//        }

        log.info("GW_FAILURE method={} path={} target={} durationMs={} error={}",
                method,
                inboundUrl(request, path),
                sanitizeUrl(targetUrl),
                durationMillis(startedAtNanos),
                exception.getMessage());
    }

    private long durationMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
    }

    private String inboundUrl(HttpServletRequest request, String path) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + sanitizeQuery(query);
    }

    private Map<String, List<String>> sanitizeRequestHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = new ArrayList<>();
            Enumeration<String> rawValues = request.getHeaders(name);
            while (rawValues != null && rawValues.hasMoreElements()) {
                values.add(sanitizeHeaderValue(name, rawValues.nextElement()));
            }
            headers.put(name, values);
        }
        return headers;
    }

    private Map<String, List<String>> sanitizeHeaders(HttpHeaders headers) {
        Map<String, List<String>> sanitized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> {
            List<String> safeValues = values.stream()
                    .map(value -> sanitizeHeaderValue(name, value))
                    .toList();
            sanitized.put(name, safeValues);
        });
        return sanitized;
    }

    private String sanitizeHeaderValue(String name, String value) {
        if (name != null && SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return MASK;
        }
        return value;
    }

    private String summarizeBody(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "<empty>";
        }
        if (!isTextLike(contentType)) {
            return "<omitted contentType=" + (contentType == null ? "unknown" : contentType) + " bytes=" + body.length + ">";
        }

        String text = new String(body, resolveCharset(contentType));
        text = redactBody(text);
        if (!fullRequestResponseLogging && maxBodyLength > 0 && text.length() > maxBodyLength) {
            return text.substring(0, maxBodyLength) + "...<truncated chars=" + text.length() + " bytes=" + body.length + ">";
        }
        return text;
    }

    private boolean shouldIncludeHeaders() {
        return fullRequestResponseLogging || includeHeaders;
    }

    private boolean shouldIncludeBodies() {
        return fullRequestResponseLogging || includeBodies;
    }

    private boolean isTextLike(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("text/")
                || lower.contains("json")
                || lower.contains("xml")
                || lower.contains("graphql")
                || lower.contains("x-www-form-urlencoded")
                || lower.contains("problem+json");
    }

    private Charset resolveCharset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return MediaType.parseMediaType(contentType).getCharset() == null
                    ? StandardCharsets.UTF_8
                    : MediaType.parseMediaType(contentType).getCharset();
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private String redactBody(String text) {
        String redacted = JSON_SECRET_FIELD_PATTERN.matcher(text).replaceAll("$1" + MASK + "$3");
        return redactFormBody(redacted);
    }

    private String redactFormBody(String text) {
        String[] parts = text.split("&", -1);
        if (parts.length <= 1) {
            return text;
        }

        boolean changed = false;
        for (int index = 0; index < parts.length; index++) {
            int equals = parts[index].indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = safeUrlDecode(parts[index].substring(0, equals)).toLowerCase(Locale.ROOT);
            if (isSensitiveName(name)) {
                parts[index] = parts[index].substring(0, equals + 1) + MASK;
                changed = true;
            }
        }
        return changed ? String.join("&", parts) : text;
    }

    private String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            String sanitizedQuery = query == null ? null : sanitizeQuery(query);
            return new URI(
                    uri.getScheme(),
                    uri.getRawAuthority(),
                    uri.getRawPath(),
                    sanitizedQuery,
                    uri.getRawFragment()
            ).toString();
        } catch (URISyntaxException ex) {
            int question = url.indexOf('?');
            if (question < 0) {
                return url;
            }
            return url.substring(0, question + 1) + sanitizeQuery(url.substring(question + 1));
        }
    }

    private String sanitizeQuery(String query) {
        String[] parts = query.split("&", -1);
        for (int index = 0; index < parts.length; index++) {
            int equals = parts[index].indexOf('=');
            String rawName = equals >= 0 ? parts[index].substring(0, equals) : parts[index];
            String name = safeUrlDecode(rawName).toLowerCase(Locale.ROOT);
            if (isSensitiveName(name)) {
                parts[index] = equals >= 0 ? rawName + "=" + MASK : rawName + "=" + MASK;
            }
        }
        return String.join("&", parts);
    }

    private boolean isSensitiveName(String name) {
        return SENSITIVE_QUERY_PARAMS.contains(name)
                || name.contains("password")
                || name.contains("secret")
                || name.contains("token")
                || name.contains("authorization")
                || name.contains("apikey")
                || name.contains("api_key");
    }

    private String safeUrlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String firstHeader(HttpHeaders headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        return headers.getFirst(name);
    }
}
