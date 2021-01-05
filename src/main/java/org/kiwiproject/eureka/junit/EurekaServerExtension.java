package org.kiwiproject.eureka.junit;

import lombok.AccessLevel;
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

    @Getter
    private int port;

    @Getter(AccessLevel.PACKAGE)
    private EmbeddedEurekaServer embeddedEurekaServer;

    public EurekaServerExtension() {
        this(0);
    }

    public EurekaServerExtension(int port) {
        LOG.trace("New EurekaServerExtension instance created");
        this.port = port;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        LOG.trace("Creating mock Eureka server");

        // This is needed so Eureka starts up very quickly
        // See: https://github.com/Netflix/eureka/issues/42#issuecomment-75614903
        System.setProperty("eureka.numberRegistrySyncRetries", "0");

        embeddedEurekaServer = new EmbeddedEurekaServer(port);
        embeddedEurekaServer.start();

        // If port passed in is a zero, then this sets the internal port to the dynamically bound port
        port = embeddedEurekaServer.getEurekaPort();

        LOG.info("Started eureka mock server at http://localhost:{}", port);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        LOG.info("Stopping mock Eureka server (running at http://localhost:{})", port);
        embeddedEurekaServer.stop();

        // Clearing the property that was set before the server was started.
        System.clearProperty("eureka.numberRegistrySyncRetries");
    }

}
