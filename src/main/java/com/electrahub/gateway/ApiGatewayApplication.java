package com.electrahub.gateway;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiGatewayApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayApplication.class);


    /**
     * Executes main for `ApiGatewayApplication`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway`.
     * @param args input consumed by main.
     */
    public static void main(String[] args) {
        LOGGER.info(" Entering ApiGatewayApplication#main");
        LOGGER.debug(" Entering ApiGatewayApplication#main with debug context");
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
