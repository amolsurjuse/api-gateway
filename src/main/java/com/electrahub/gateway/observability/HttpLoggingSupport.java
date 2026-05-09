package com.electrahub.gateway.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

public final class HttpLoggingSupport {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-internal-api-key",
            "x-api-key",
            "x-auth-token",
            "x-csrf-token"
    );

    private static final int MAX_HEADER_VALUE_LENGTH = 160;

    private HttpLoggingSupport() {
    }

    public static String formatRequest(HttpServletRequest request) {
        if (request == null) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {
                joiner.add(name + "=" + formatHeaderValue(name, Collections.list(request.getHeaders(name))));
            }
        }
        return joiner.toString();
    }

    public static String formatResponse(HttpServletResponse response) {
        if (response == null) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (String name : response.getHeaderNames()) {
            joiner.add(name + "=" + formatHeaderValue(name, response.getHeaders(name)));
        }
        return joiner.toString();
    }

    public static String formatHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        headers.forEach((name, values) -> joiner.add(name + "=" + formatHeaderValue(name, values)));
        return joiner.toString();
    }

    private static String formatHeaderValue(String name, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        if (isSensitive(name)) {
            return "[REDACTED]";
        }

        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (String value : values) {
            if (value == null) {
                joiner.add("null");
                continue;
            }
            joiner.add(value.length() <= MAX_HEADER_VALUE_LENGTH ? value : value.substring(0, MAX_HEADER_VALUE_LENGTH) + "...");
        }
        return joiner.toString();
    }

    private static boolean isSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }
        String normalized = headerName.toLowerCase(Locale.ROOT);
        if (SENSITIVE_HEADERS.contains(normalized)) {
            return true;
        }
        return normalized.contains("token") || normalized.contains("secret") || normalized.contains("password");
    }
}
