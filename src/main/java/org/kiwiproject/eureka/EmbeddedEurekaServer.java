package org.kiwiproject.eureka;

import static com.google.common.base.Preconditions.checkState;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.kiwiproject.io.KiwiPaths;

import java.util.concurrent.TimeUnit;

/**
 * Creates, configures, starts and stops an embedded Jetty server running a Eureka Server.
 * <p>
 * If using this class outside of {@link org.kiwiproject.eureka.junit.EurekaServerExtension} then you may want to
 * set the eureka.numberRegistrySyncRetries to zero in the System properties. This will allow the server to spin up
 * quickly an not try to connect to other non-existent Eureka servers.
 * See https://github.com/Netflix/eureka/issues/42#issuecomment-75614903 for dialog on this.
 */
@Slf4j
public class EmbeddedEurekaServer {

    private static final long IDLE_TIMEOUT = TimeUnit.HOURS.toMillis(1L);
    private static final String DEFAULT_CONTEXT_PATH = "/";

    private final Server eurekaServer;
    private final ServerConnector connector;

    @Getter
    private final EmbeddedEurekaBootstrap registry;

    public EmbeddedEurekaServer() {
        this(DEFAULT_CONTEXT_PATH);
    }

    /**
     * Creates a new EmbeddedEurekaServer allowing Jetty to pick an available port.
     */
    public EmbeddedEurekaServer(String basePath) {
        eurekaServer = new Server();
        connector = new ServerConnector(eurekaServer);

        // Set timeout option to make debugging easier.
        connector.setIdleTimeout(IDLE_TIMEOUT);

        connector.setPort(0);
        eurekaServer.setConnectors(new Connector[] { connector });

        var webContext = new WebAppContext();
        webContext.setContextPath(basePath);

        var webXml = KiwiPaths.pathFromResourceName("webapp");
        webContext.setResourceBase(webXml.toString());

        registry = new EmbeddedEurekaBootstrap();
        webContext.addEventListener(registry);

        eurekaServer.setHandler(webContext);
    }

    /**
     * Starts the server.
     */
    public void start() {
        try {
            eurekaServer.start();
        } catch (Exception e) {
            LOG.error("Error starting Eureka", e);
            throw new IllegalStateException("Eureka has not been started", e);
        }
    }

    /**
     * Stops the server.
     *
     * @throws IllegalStateException when the server is not running.
     */
    public void stop() {
        checkState(eurekaServer.isStarted(), "Server has not been started. Call start first!");

        try {
            eurekaServer.stop();
            eurekaServer.join();
        } catch (Exception e) {
            LOG.error("Error shutting down Eureka", e);
        }
    }

    /**
     * Retrieves the port that the server is listening on.
     *
     * @return the server port.
     */
    public int getEurekaPort() {
        return connector.getLocalPort();
    }

    /**
     * Returns whether the server is running or not.
     *
     * @return true if the server is running, false otherwise.
     */
    public boolean isStarted() {
        return eurekaServer.isStarted();
    }
}
