package com.electrahub.gateway.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Aspect
@Component
public class ObservabilityAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityAspect.class);
    private static final int MAX_LOG_VALUE_LENGTH = 300;

    private final MeterRegistry meterRegistry;

    public ObservabilityAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(public * com.electrahub..*.*(..)) && " +
            "(within(@org.springframework.web.bind.annotation.RestController *) || " +
            "within(@org.springframework.stereotype.Controller *) || " +
            "within(@org.springframework.stereotype.Service *) || " +
            "within(@org.springframework.stereotype.Repository *) || " +
            "within(@org.springframework.stereotype.Component *)) && " +
            "!within(com.electrahub..observability..*)")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        long startedAt = System.nanoTime();

        Counter.builder("electrahub.method.invocations")
                .description("Total method invocations for Spring-managed application beans")
                .tag("class", className)
                .tag("method", methodName)
                .register(meterRegistry)
                .increment();

        LOGGER.info("Starting {}.{}", className, methodName);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Arguments for {}.{} -> {}", className, methodName, formatArgs(joinPoint.getArgs()));
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Object result = joinPoint.proceed();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            sample.stop(Timer.builder("electrahub.method.duration")
                    .description("Method execution duration for Spring-managed application beans")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("outcome", "success")
                    .register(meterRegistry));

            LOGGER.info("Completed {}.{} in {} ms", className, methodName, durationMs);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Result for {}.{} -> {}", className, methodName, abbreviate(result));
            }
            return result;
        } catch (Throwable ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            sample.stop(Timer.builder("electrahub.method.duration")
                    .description("Method execution duration for Spring-managed application beans")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("outcome", "failure")
                    .register(meterRegistry));

            Counter.builder("electrahub.method.failures")
                    .description("Total failed method invocations for Spring-managed application beans")
                    .tag("class", className)
                    .tag("method", methodName)
                    .register(meterRegistry)
                    .increment();

            LOGGER.info("Failed {}.{} in {} ms: {}", className, methodName, durationMs, ex.toString());
            LOGGER.debug("Failure stack trace for {}.{}", className, methodName, ex);
            throw ex;
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(this::abbreviate)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String abbreviate(Object value) {
        if (value == null) {
            return "null";
        }
        String text;
        try {
            text = value.toString();
        } catch (Exception ex) {
            return value.getClass().getSimpleName();
        }
        if (text.length() <= MAX_LOG_VALUE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }
}
