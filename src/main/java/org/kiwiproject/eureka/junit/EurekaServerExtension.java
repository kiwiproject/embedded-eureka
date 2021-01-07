package org.kiwiproject.eureka.junit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.eureka.EmbeddedEurekaServer;

/**
 * JUnit Jupiter extension that starts a local Eureka testing server before any test has run, and stops the server
 * after all tests have (successfully or otherwise) completed.
 */
@Slf4j
public class EurekaServerExtension implements BeforeAllCallback, AfterAllCallback {

    /**
     * The base path at which the testing Eureka server will respond to requests.
     */
    @SuppressWarnings("java:S1075")
    public static final String EUREKA_API_BASE_PATH = "/eureka/";

    @Getter
    private int port;

    @Getter
    private EmbeddedEurekaServer eurekaServer;

    @Getter
    private final String basePath;

    public EurekaServerExtension() {
        this(EUREKA_API_BASE_PATH);
    }

    public EurekaServerExtension(String basePath) {
        LOG.trace("New EurekaServerExtension instance created");
        this.basePath = basePath;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        LOG.trace("Creating mock Eureka server");

        eurekaServer = new EmbeddedEurekaServer(basePath);
        eurekaServer.start();

        port = eurekaServer.getEurekaPort();

        LOG.info("Started eureka mock server at http://localhost:{}{}", port, basePath);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        LOG.info("Stopping mock Eureka server (running at http://localhost:{}{})", port, basePath);
        eurekaServer.stop();
    }

    // TODO: We may need to implement a beforeEach to run the "hack" found in EurekaTestHelpers

}
