package org.kiwiproject.eureka;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.kiwiproject.jaxrs.KiwiResponses.notSuccessful;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class EurekaServer {

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<String, Application> applications = new ConcurrentHashMap<>();

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<String, InstanceInfo> heartbeatApps = new ConcurrentHashMap<>();

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final List<String> heartbeatHistory = Collections.synchronizedList(new ArrayList<>());

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final AtomicInteger heartbeatCount = new AtomicInteger();

    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    private final AtomicInteger heartbeatFailureCount = new AtomicInteger();

    public Optional<InstanceInfo> getInstance(String appId, String instanceId) {
        var application = MapUtils.getObject(applications, appId);

        if (nonNull(application)) {
            var instanceInfo = application.getByInstanceId(instanceId);
            return Optional.ofNullable(instanceInfo);
        }

        return Optional.empty();
    }

    public List<Application> applicationsThatMatchVipAddressFromPath(String path) {
        return applications.values().stream()
                .filter(application -> allApplicationInstancesMatchVipAddressInPath(application, path))
                .collect(toUnmodifiableList());
    }

    private static boolean allApplicationInstancesMatchVipAddressInPath(Application application, String path) {
        var result = application.getInstances().stream()
                .allMatch(instanceInfo -> path.endsWith(instanceInfo.getVIPAddress()));

        LOG.trace("Do all instances of application {} match VIP in path {}? {}", application.getName(), path, result);
        return result;
    }

    /**
     * Clears all applications, heartbeats, and heartbeat history from the running Eureka server. Also resets the
     * heartbeat failure count to zero.
     * <p>
     * If you want a "fresh" Eureka server for each test, then call this in a {@link org.junit.jupiter.api.BeforeEach}
     * or {@link org.junit.jupiter.api.AfterEach} method.
     */
    public void cleanupApps() {
        applications.clear();
        heartbeatApps.clear();
        heartbeatHistory.clear();
        heartbeatCount.set(0);
        heartbeatFailureCount.set(0);
    }

    public void updateHeartbeatFor(String appName, String hostName, int statusCode, InstanceInfo.InstanceStatus instanceStatus) {
        var heartbeatAppKey = heartbeatAppKey(appName, hostName);
        heartbeatHistory.add(heartbeatAppHistoryKey(heartbeatAppKey, statusCode));
        heartbeatCount.incrementAndGet();

        if (notSuccessful(statusCode)) {
            var newFailureCount = heartbeatFailureCount.incrementAndGet();
            LOG.debug("Return status {} on heartbeat for app {}, instance {} ; new failure count: {}",
                    statusCode, appName, hostName, newFailureCount);
        } else {
            var builder = InstanceInfo.Builder.newBuilder()
                    .setAppName(appName)
                    .setHostName(hostName)
                    .setStatus(instanceStatus);
            var updatedInstanceInfo = builder.build();
            heartbeatApps.put(heartbeatAppKey, updatedInstanceInfo);
        }
    }

    private static String heartbeatAppKey(String app, String instance) {
        return app + "|" + instance;
    }

    private String heartbeatAppHistoryKey(String appKey, int statusCode) {
        return appKey + "|" + statusCode + "|" + ISO_DATE_TIME.format(LocalDateTime.now());
    }

    public void registerApplication(InstanceInfo instance) {
        applications.computeIfAbsent(instance.getAppName(), appName -> newApplication(appName, instance));
    }

    private Application newApplication(String appName, InstanceInfo instance) {
        var app = new Application(appName);
        app.addInstance(instance);
        return app;
    }

    public Optional<Application> getApplicationByName(String appName) {
        return Optional.ofNullable(applications.get(appName));
    }

    public void unregisterApplication(Application application, String appName, String hostName) {
        application.removeInstance(InstanceInfo.Builder.newBuilder().setAppName(appName).setHostName(hostName).build());
        if (application.getInstances().isEmpty()) {
            applications.remove(appName);
        }
        heartbeatApps.remove(heartbeatAppKey(appName, hostName));
    }
}
