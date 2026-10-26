package com.pki.ca.bootstrap;

import com.pki.ca.service.CaAuthorityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the isolated test CA hierarchy (Acme Offline Root CA -> Enterprise Issuing CA 01)
 * on application startup if an active CA is not yet established.
 */
@Component
@Order(20)
public class CaBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CaBootstrapRunner.class);

    private final CaAuthorityService caAuthorityService;

    @Value("${app.ca.auto-bootstrap.enabled:true}")
    private boolean autoBootstrapEnabled;

    public CaBootstrapRunner(CaAuthorityService caAuthorityService) {
        this.caAuthorityService = caAuthorityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoBootstrapEnabled) {
            log.info("CA auto-bootstrap is disabled by configuration.");
            return;
        }

        try {
            caAuthorityService.ensureDefaultTestCaHierarchy();
        } catch (Exception ex) {
            log.error("Failed to bootstrap CA hierarchy: {}", ex.getMessage(), ex);
            // In development or integration testing, surface error
        }
    }
}
