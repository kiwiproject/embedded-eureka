package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("EurekaServer")
class EurekaServerTest {

    private EurekaServer server;

    @BeforeEach
    void setUp() {
        server = new EurekaServer();
    }

    @Nested
    class GetInstance {

        @Test
        void shouldReturnOptionalEmptyWhenAppIdIsNotFound() {
            assertThat(server.getInstance("dummy-app", "dummy-instance")).isEmpty();
        }

        @Test
        void shouldReturnOptionalEmptyWhenAppIdIsFoundButNotInstanceId() {
            server.getApplications().put("valid-app", new Application());

            assertThat(server.getInstance("valid-app", "dummy-instance")).isEmpty();
        }

        @Test
        void shouldReturnApplicationWhenAppIdAndInstanceIdAreFound() {
            var instance = InstanceInfo.Builder.newBuilder().setAppName("valid-app").setInstanceId("valid-instance").build();
            var application = new Application("valid-app", List.of(instance));

            server.getApplications().put("valid-app", application);

            assertThat(server.getInstance("valid-app", "valid-instance")).isPresent();
        }
    }

    @Nested
    class ApplicationsThatMatchVipAddress {

        @Test
        void shouldReturnEmptyListWhenNoApplicationsFoundBecauseApplicationsIsEmpty() {
            var applications = server.applicationsThatMatchVipAddressFromPath("dummy-vipAddress");
            assertThat(applications).isEmpty();
        }

        @Test
        void shouldReturnEmptyListWhenNoApplicationsFoundWhenApplicationsLoaded() {
            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName("valid-app")
                    .setVIPAddress("some-vip-address")
                    .setInstanceId("valid-instance")
                    .build();

            var application = new Application("valid-app", List.of(instance));

            server.getApplications().put("valid-app", application);

            var applications = server.applicationsThatMatchVipAddressFromPath("dummy-vipAddress");
            assertThat(applications).isEmpty();
        }

        @Test
        void shouldReturnListOfFoundApplicationsThatMatch() {
            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName("valid-app")
                    .setVIPAddress("some-vip-address")
                    .setInstanceId("valid-instance")
                    .build();

            var application = new Application("valid-app", List.of(instance));

            server.getApplications().put("valid-app", application);

            var applications = server.applicationsThatMatchVipAddressFromPath("some-vip-address");
            assertThat(applications).contains(application);
        }
    }

    @Nested
    class CleanupApps {

        @Test
        void shouldMakeSureInternalMapsAreCleared() {
            server.getApplications().put("test", new Application("test"));
            server.getHeartbeatApps().put("test", InstanceInfo.Builder.newBuilder().setAppName("test").build());
            server.getHeartbeatCount().set(5);
            server.getHeartbeatFailureCount().set(5);
            server.getHeartbeatHistory().add("test");

            server.cleanupApps();

            assertThat(server.getApplications()).isEmpty();
            assertThat(server.getHeartbeatApps()).isEmpty();
            assertThat(server.getHeartbeatHistory()).isEmpty();
            assertThat(server.getHeartbeatCount()).hasValue(0);
            assertThat(server.getHeartbeatFailureCount()).hasValue(0);
        }

    }
    
    @Nested
    class UpdateHeartbeatFor {
        
        @Test
        void shouldUpdateHeartbeatFailureCountOnErrorStatus() {

            server.updateHeartbeatFor("test-service", "localhost", 500, InstanceInfo.InstanceStatus.UP);

            assertThat(server.getHeartbeatHistory()).hasSize(1);
            assertThat(server.getHeartbeatHistory().get(0)).startsWith("test-service|localhost|500|");
            assertThat(server.getHeartbeatCount()).hasValue(1);
            assertThat(server.getHeartbeatFailureCount()).hasValue(1);
            assertThat(server.getHeartbeatApps()).isEmpty();
        }

        @Test
        void shouldUpdateHeartbeatAppsOnSuccess() {
            server.updateHeartbeatFor("test-service", "localhost", 200, InstanceInfo.InstanceStatus.UP);

            assertThat(server.getHeartbeatHistory()).hasSize(1);
            assertThat(server.getHeartbeatHistory().get(0)).startsWith("test-service|localhost|200|");
            assertThat(server.getHeartbeatCount()).hasValue(1);
            assertThat(server.getHeartbeatFailureCount()).hasValue(0);
            assertThat(server.getHeartbeatApps()).contains(entry("test-service|localhost", InstanceInfo.Builder.newBuilder()
                    .setAppName("test-service")
                    .setHostName("localhost")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .build()));
        }
    }

    @Nested
    class RegisterApplication {

        @Test
        void shouldAddApplicationIfDoesNotExist() {
            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName("valid-app")
                    .setVIPAddress("some-vip-address")
                    .setInstanceId("valid-instance")
                    .build();

            server.registerApplication(instance);

            assertThat(server.getApplications()).containsKey("VALID-APP");
            assertThat(server.getApplications().get("VALID-APP").getByInstanceId("valid-instance")).isNotNull();
        }

        @Test
        void shouldNotAddApplicationIfExists() {
            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName("valid-app")
                    .setVIPAddress("some-vip-address")
                    .setInstanceId("valid-instance")
                    .build();

            var application = new Application("VALID-APP", List.of(instance));
            server.getApplications().put("VALID-APP", application);

            var newInstanceDifferentInstanceId = InstanceInfo.Builder.newBuilder()
                    .setAppName("valid-app")
                    .setVIPAddress("some-vip-address")
                    .setInstanceId("nope")
                    .build();

            server.registerApplication(newInstanceDifferentInstanceId);

            assertThat(server.getApplications()).containsKey("VALID-APP");
            assertThat(server.getApplications().get("VALID-APP").getByInstanceId("valid-instance")).isNotNull();
            assertThat(server.getApplications().get("VALID-APP").getByInstanceId("nope")).isNull();
        }
    }
}
