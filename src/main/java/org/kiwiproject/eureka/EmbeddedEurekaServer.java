package org.kiwiproject.eureka;

import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.io.KiwiPaths.pathFromResourceName;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.Jersey2DiscoveryClientOptionalArgs;
import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.netflix.discovery.shared.transport.jersey2.Jersey2TransportClientFactories;
import com.netflix.eureka.resources.EurekaServerContextBinder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.DispatcherType;
import java.util.EnumSet;
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
    private ServerConnector connector;

    @Getter
    private EmbeddedEurekaBootstrap registry;

    public EmbeddedEurekaServer() {
        this(DEFAULT_CONTEXT_PATH);
    }

    /**
     * Creates a new EmbeddedEurekaServer allowing Jetty to pick an available port.
     */
    public EmbeddedEurekaServer(String basePath) {
        eurekaServer = new Server();
        setupConnector();

        var webContext = new WebAppContext();
        webContext.setContextPath(basePath);
        webContext.setResourceBase(pathFromResourceName("webapp").toString());

        buildEurekaBootstrap();
        webContext.addEventListener(registry);

        configureApi(webContext);

        eurekaServer.setHandler(webContext);
    }

    private void setupConnector() {
        connector = new ServerConnector(eurekaServer);

        // Set timeout option to make debugging easier.
        connector.setIdleTimeout(IDLE_TIMEOUT);

        connector.setPort(0);
        eurekaServer.setConnectors(new Connector[] { connector });
    }

    private void buildEurekaBootstrap() {
        var instanceConfig = new MyDataCenterInstanceConfig("embeddedEureka");
        var instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();

        var applicationInfoManager = new ApplicationInfoManager(instanceConfig, instanceInfo);
        var args = new Jersey2DiscoveryClientOptionalArgs();
        args.setTransportClientFactories(new Jersey2TransportClientFactories());
        var discoveryClient = new DiscoveryClient(applicationInfoManager, new DefaultEurekaClientConfig(), args);

        registry = new EmbeddedEurekaBootstrap(discoveryClient);
    }

    private void configureApi(WebAppContext webContext) {
        var resourceConfig = new ResourceConfig();
        resourceConfig.packages("com.netflix");
        resourceConfig.register(new EurekaServerContextBinder());
        resourceConfig.register(DiscoveryJerseyProvider.class);

        var resourceServletContext = new ServletContainer(resourceConfig);
        var filterHolder = new FilterHolder(resourceServletContext);
        webContext.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
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

    public boolean isStopped() {
        return eurekaServer.isStopped();
    }
}
