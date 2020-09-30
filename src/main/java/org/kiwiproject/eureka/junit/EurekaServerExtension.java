package org.kiwiproject.eureka.junit;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.eureka.EurekaServer;
import org.kiwiproject.eureka.EurekaServletHandler;

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
    public static final String EUREKA_API_BASE_PATH = "/eureka/v2/";

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private Server server;

    @Getter
    private int port;

    @Getter
    private EurekaServer eurekaServer;

    public EurekaServerExtension() {
        LOG.trace("New EurekaServerExtension instance created");
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        LOG.trace("Creating mock Eureka server");

        createServer();
        server.start();
        port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();

        LOG.info("Started eureka mock server at {}", server.getURI());
    }

    private void createServer() {
        eurekaServer = new EurekaServer();

        var eurekaHandler = new EurekaServletHandler(eurekaServer);
        var holder = new ServletHolder("eurekaHandler", eurekaHandler);

        var handler = new ServletHandler();
        handler.addServletWithMapping(holder, EUREKA_API_BASE_PATH + "*");

        server = new Server(0);
        server.setHandler(handler);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        LOG.info("Stopping mock Eureka server (running at {})", server.getURI());
        server.stop();
    }

}
