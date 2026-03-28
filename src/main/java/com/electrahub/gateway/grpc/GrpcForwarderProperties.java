package com.electrahub.gateway.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for gRPC forwarders.
 * Allows enabling/disabling individual service forwarders via application.yaml
 *
 * Example configuration:
 * app:
 *   grpc:
 *     forwarders:
 *       auth:
 *         enabled: true
 *       user:
 *         enabled: true
 *       payment:
 *         enabled: false
 */
@Component
@ConfigurationProperties(prefix = "app.grpc.forwarders")
public class GrpcForwarderProperties {

    private Forwarder auth = new Forwarder();
    private Forwarder user = new Forwarder();
    private Forwarder payment = new Forwarder();
    private Forwarder subscription = new Forwarder();
    private Forwarder charger = new Forwarder();
    private Forwarder pricing = new Forwarder();

    public Forwarder getAuth() {
        return auth;
    }

    public void setAuth(Forwarder auth) {
        this.auth = auth;
    }

    public Forwarder getUser() {
        return user;
    }

    public void setUser(Forwarder user) {
        this.user = user;
    }

    public Forwarder getPayment() {
        return payment;
    }

    public void setPayment(Forwarder payment) {
        this.payment = payment;
    }

    public Forwarder getSubscription() {
        return subscription;
    }

    public void setSubscription(Forwarder subscription) {
        this.subscription = subscription;
    }

    public Forwarder getCharger() {
        return charger;
    }

    public void setCharger(Forwarder charger) {
        this.charger = charger;
    }

    public Forwarder getPricing() {
        return pricing;
    }

    public void setPricing(Forwarder pricing) {
        this.pricing = pricing;
    }

    /**
     * Configuration for individual forwarder.
     */
    public static class Forwarder {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
