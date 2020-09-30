package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;

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

            server.cleanupApps();

            assertThat(server.getApplications()).isEmpty();
        }

    }
}
