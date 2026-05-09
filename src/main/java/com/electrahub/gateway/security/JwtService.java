package com.electrahub.gateway.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

@Service
public class JwtService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtService.class);


    private final Key signingKey;
    private final String issuer;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.issuer}") String issuer
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    public record ParsedToken(String subjectEmail, String jti, String uid, long tv, Date exp, List<String> roles) {}

    /**
     * Executes parse and validate for `JwtService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param token input consumed by parseAndValidate.
     * @return result produced by parseAndValidate.
     */
    public ParsedToken parseAndValidate(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey)
                .build()
                .parseSignedClaims(token);

        Claims c = jws.getPayload();

        if (!issuer.equals(c.getIssuer())) {
            LOGGER.warn("JWT rejected because issuer did not match expected value");
            throw new JwtException("Invalid issuer");
        }

        Object tvObj = c.getOrDefault("tv", 0);
        long tv = (tvObj instanceof Number n) ? n.longValue() : Long.parseLong(String.valueOf(tvObj));

        Object rolesObj = c.getOrDefault("roles", List.of());
        List<String> roles = switch (rolesObj) {
            case List<?> list -> list.stream().map(String::valueOf).toList();
            case String s -> List.of(s);
            case null -> List.of();
            default -> List.of(String.valueOf(rolesObj));
        };

        ParsedToken parsedToken = new ParsedToken(
                c.getSubject(),
                c.getId(),
                String.valueOf(c.get("uid")),
                tv,
                c.getExpiration(),
                roles
        );
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Parsed JWT successfully: subject={} uid={} roles={}", parsedToken.subjectEmail(), parsedToken.uid(), parsedToken.roles());
        }
        return parsedToken;
    }

    /**
     * Executes is not expired for `JwtService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param exp input consumed by isNotExpired.
     * @return result produced by isNotExpired.
     */
    public boolean isNotExpired(Date exp) {
        return exp != null && exp.after(new Date());
    }
}
