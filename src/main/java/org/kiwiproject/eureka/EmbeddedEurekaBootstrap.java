package org.kiwiproject.eureka;

import static java.util.Objects.nonNull;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.Jersey2EurekaBootStrap;
import com.netflix.eureka.registry.AbstractInstanceRegistry;
import com.netflix.eureka.util.MeasuredRate;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extension of Eureka's bootstrap but with added accessors to validate internal Eureka data.
 */
@Slf4j
public class EmbeddedEurekaBootstrap extends Jersey2EurekaBootStrap {

    public EmbeddedEurekaBootstrap(DiscoveryClient client) {
        super(client);
    }

    /**
     * Cleans out all the registered applications inside of Eureka.
     */
    public void cleanupApps() {
        LOG.info("Clearing registry");
        serverContext.getRegistry().clearRegistry();
    }

    /**
     * Returns a list of all the registered applications in Eureka.
     *
     * @return list of registered applications.
     */
    public List<Application> registeredApplications() {
        return serverContext.getRegistry().getApplications().getRegisteredApplications();
    }

    /**
     * Checks to see if a given app is registered in Eureka.
     *
     * @param appId the id of the app to check.
     * @return true if registered, false otherwise.
     */
    public boolean isApplicationRegistered(String appId) {
        return nonNull(serverContext.getRegistry().getApplication(appId));
    }

    /**
     * Returns an {@link Application} with given appId if found from Eureka.
     *
     * @param appId the id of the app to check.
     * @return the registered application if found, otherwise null.
     */
    public Application getRegisteredApplication(String appId) {
        return serverContext.getRegistry().getApplication(appId);
    }

    /**
     * Returns the count of heartbeat renewals in Eureka.
     *
     * @return the number of heartbeats sent to Eureka
     * @implNote This requires reflection hacks due to the way Eureka stores the renews and what is visible from
     * this class.  I don't like it, but it is what it is.  The {@code getNumOfRenewsInLastMin()} method calls
     * {@code getCount()} on the {@link MeasuredRate} which returns the value from the {@link AtomicLong}
     * {@code lastBucket}. When renews happen, the increment happens on teh {@link AtomicLong} {@code currentBucket}
     * field which is not publicly accessible.
     */
    @SuppressWarnings("java:S3011")
    public long getHeartbeatCount() {
        try {
            var renewsInLastMinField = AbstractInstanceRegistry.class.getDeclaredField("renewsLastMin");
            renewsInLastMinField.setAccessible(true); // Suppress Java language access checking

            // Remove "final" modifier
            var modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(renewsInLastMinField, renewsInLastMinField.getModifiers() & ~Modifier.FINAL);

            var value = (MeasuredRate) renewsInLastMinField.get(serverContext.getRegistry());

            var currentBucket = MeasuredRate.class.getDeclaredField("currentBucket");
            currentBucket.setAccessible(true);

            // Remove "final" modifier
            modifiersField.setInt(currentBucket, currentBucket.getModifiers() & ~Modifier.FINAL);

            return ((AtomicLong) currentBucket.get(value)).get();
        } catch (Exception e) {
            LOG.error("Error finding heartbeat count", e);
            return -1;
        }
    }

    /**
     * Loads an application instance in Eureka for later retrieval.
     *
     * @param appName       the application name
     * @param instanceId    the instance id
     * @param vipAddress    the VIP address of the instance
     * @param status        the status of the instance
     */
    public void registerApplication(String appName, String instanceId, String vipAddress, String status) {
        var instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName(appName)
                .setInstanceId(instanceId)
                .setHostName(instanceId)
                .setVIPAddress(vipAddress)
                .setStatus(InstanceInfo.InstanceStatus.valueOf(status))
                .build();

        serverContext.getRegistry().register(instanceInfo, false);
    }
}
