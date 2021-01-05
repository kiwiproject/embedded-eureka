package org.kiwiproject.eureka;

import static com.google.common.base.Preconditions.checkState;

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
