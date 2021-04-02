package org.kiwiproject.eureka.junit;

import static java.util.Objects.nonNull;

import com.netflix.discovery.shared.Application;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.eureka.EmbeddedEurekaServer;
import org.kiwiproject.eureka.EurekaTestHelpers;

import java.util.List;

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
        LOG.trace("[beforeAll: {}] Initialize testing server.", displayName);

        if (nonNull(eurekaServer) && eurekaServer.isStarted()) {
            LOG.trace("[beforeAll: {}] Skip initialization since server is STARTED. Maybe we are in a @Nested test class?",
                    displayName);
            return;
        } else if (nonNull(eurekaServer) && eurekaServer.isStopped()) {
            LOG.trace("[beforeAll: {}] Re-initialize since server is STOPPED. There is probably more than one @Nested test class.",
                    displayName);
        }

        LOG.trace("[beforeAll: {}] Starting Eureka Mock Server", displayName);
        eurekaServer = new EmbeddedEurekaServer(basePath);
        eurekaServer.start();

        port = eurekaServer.getEurekaPort();

        LOG.trace("[beforeAll: {}] Started Eureka Mock Server at http://localhost:{}{}", displayName, port, basePath);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var displayName = context.getDisplayName();

        LOG.trace("[afterAll: {}] Stopping Eureka Mock Server (running at http://localhost:{}{})", displayName, port, basePath);
        eurekaServer.stop();
        LOG.trace("[afterAll: {}] Eureka Mock Server stopped!", displayName);

        // Reset static executor inside of Eureka
        EurekaTestHelpers.resetStatsMonitor();
    }

    /**
     * Helper method to access {@link EmbeddedEurekaServer#getRegistry()}'s {@code clearRegisteredApps()}.
     */
    public void clearRegisteredApps() {
        eurekaServer.getRegistry().clearRegisteredApps();
    }

    /**
     * Helper method to access {@link EmbeddedEurekaServer#getRegistry()}'s
     * {@code registerApplication(appName, instanceId, vipAddress, status)}.
     */
    public void registerApplication(String appName, String instanceId, String vipAddress, String status) {
        eurekaServer.getRegistry().registerApplication(appName, instanceId, vipAddress, status);
    }

    /**
     * Helper method to access {@link EmbeddedEurekaServer#getRegistry()}'s {@code registeredApplications()}.
     */
    public List<Application> getRegisteredApplications() {
        return eurekaServer.getRegistry().registeredApplications();
    }

    /**
     * Helper method to access {@link EmbeddedEurekaServer#getRegistry()}'s {@code getRegisteredApplication(appId)}.
     */
    public Application getRegisteredApplication(String appId) {
        return eurekaServer.getRegistry().getRegisteredApplication(appId);
    }

    /**
     * Helper method to access {@link EmbeddedEurekaServer#getRegistry()}'s {@code isApplicationRegistered(appId)}.
     */
    public boolean isApplicationRegistered(String appId) {
        return eurekaServer.getRegistry().isApplicationRegistered(appId);
    }

    /**
     * Helper method to access {@link EmbeddedEurekaServer#getRegistry()}'s {@code getHeartbeatCount()}.
     */
    public long getHeartbeatCount() {
        return eurekaServer.getRegistry().getHeartbeatCount();
    }

}
