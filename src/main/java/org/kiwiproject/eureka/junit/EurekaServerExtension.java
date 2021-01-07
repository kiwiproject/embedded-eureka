package org.kiwiproject.eureka.junit;

import static java.util.Objects.nonNull;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.eureka.EmbeddedEurekaServer;
import org.kiwiproject.eureka.EurekaTestHelpers;

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
        var displayName = context.getDisplayName();
        LOG.debug("[beforeAll: {}] Initialize testing server.", displayName);

        if (nonNull(eurekaServer) && eurekaServer.isStarted()) {
            LOG.debug("[beforeAll: {}] Skip initialization since server is STARTED. Maybe we are in a @Nested test class?",
                    displayName);
            return;
        } else if (nonNull(eurekaServer)  && eurekaServer.isStopped()) {
            LOG.debug("[beforeAll: {}] Re-initialize since server is STOPPED. There is probably more than one @Nested test class.",
                    displayName);

            EurekaTestHelpers.resetStatsMonitor();
        }

        LOG.debug("[beforeAll: {}] Starting Eureka Mock Server", displayName);
        eurekaServer = new EmbeddedEurekaServer(basePath);
        eurekaServer.start();

        port = eurekaServer.getEurekaPort();

        LOG.info("[beforeAll: {}] Started Eureka Mock Server at http://localhost:{}{}", displayName, port, basePath);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var displayName = context.getDisplayName();

        LOG.info("[afterAll: {}] Stopping Eureka Mock Server (running at http://localhost:{}{})", displayName, port, basePath);
        eurekaServer.stop();
        LOG.info("[afterAll: {}] Eureka Mock Server stopped!", displayName);

    }

}
