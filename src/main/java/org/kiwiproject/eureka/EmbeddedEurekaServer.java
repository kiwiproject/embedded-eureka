package org.kiwiproject.eureka;

import static com.google.common.base.Preconditions.checkState;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.kiwiproject.io.KiwiPaths;

/**
 * Creates, configures, starts and stops an embedded Jetty server running a Eureka Server.
 */
@Slf4j
public class EmbeddedEurekaServer {

    private static final long IDLE_TIMEOUT = 3_600_000L;

    private final Server eurekaServer;
    private final ServerConnector connector;

    /**
     * Creates a new EmbeddedEurekaServer allowing Jetty to pick an available port.
     */
    public EmbeddedEurekaServer() {
        this(0);
    }

    /**
     * Creates a new EmbeddedEurekaServer with the given port.
     *
     * @param port the port to run Jetty on.
     */
    public EmbeddedEurekaServer(int port) {
        // This is needed so Eureka starts up very quickly
        System.setProperty("eureka.numberRegistrySyncRetries", "0");

        eurekaServer = new Server();
        connector = new ServerConnector(eurekaServer);

        // Set timeout option to make debugging easier.
        connector.setIdleTimeout(IDLE_TIMEOUT);

        connector.setPort(port);
        eurekaServer.setConnectors(new Connector[] { connector });

        var context = new WebAppContext();
        context.setServer(eurekaServer);
        context.setContextPath("/");
        context.setWar(KiwiPaths.pathFromResourceName("eureka-server.war").toString());

        eurekaServer.setHandler(context);
    }

    /**
     * Starts the server.
     */
    public void start() {
        try {
            eurekaServer.start();
        } catch (Exception e) {
            LOG.error("Error while starting down Eureka", e);
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
            LOG.error("Error while shutting down Eureka", e);
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
