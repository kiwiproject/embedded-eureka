package org.kiwiproject.eureka;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class EurekaServer {

    @Getter
    private final ConcurrentMap<String, Application> applications = new ConcurrentHashMap<>();

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

        // TODO: These will be uncommented as I implement the rest of the functionality
//        heartbeatApps.clear();
//        heartbeatHistory.clear();
//        heartbeatCount.set(0);
//        heartbeatFailureCount.set(0);
    }
}
