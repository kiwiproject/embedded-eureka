package org.kiwiproject.eureka.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.Mockito.mock;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.eureka.EurekaTestHelpers;
import org.kiwiproject.net.KiwiUrls;

import javax.ws.rs.client.ClientBuilder;

@Slf4j
@DisplayName("EurekaServerExtension")
class EurekaServerExtensionTest {

    @BeforeEach
    void setUp() {
        EurekaTestHelpers.resetStatsMonitor();
    }

    @Nested
    class BeforeAllMethod {

        @Test
        void shouldCreateTheEurekaServerInstanceAndStartARealServer() {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            try {
                extension.beforeAll(context);

                var server = extension.getEurekaServer();

                assertThat(server).isNotNull();
                assertThat(server.isStarted()).isTrue();
                assertThat(extension.getPort()).isPositive();
                assertThat(extension.getBasePath()).isEqualTo(EurekaServerExtension.EUREKA_API_BASE_PATH);

                var client = ClientBuilder.newClient();

                var url = KiwiUrls.createHttpUrl("localhost", server.getEurekaPort());
                var response = client.target(url)
                        .path("/eureka/v2/apps")
                        .request()
                        .get();

                assertOkResponse(response);
            } finally {
                extension.getEurekaServer().stop();
            }

        }

    }

    @Nested
    class AfterAllMethod {

        @Test
        void shouldStopTheServer() {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            extension.beforeAll(context);
            assertThat(extension.getEurekaServer().isStarted()).isTrue();

            var client = ClientBuilder.newClient();

            var url = KiwiUrls.createHttpUrl("localhost", extension.getEurekaServer().getEurekaPort());
            var response = client.target(url)
                    .path("/eureka/v2/apps")
                    .request()
                    .get();

            assertOkResponse(response);

            extension.afterAll(context);
            assertThat(extension.getEurekaServer().isStopped()).isTrue();
        }

    }

    @Nested
    class UtilityMethods {

        @Test
        void shouldCallCorrectMethodsOnRegistry() {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            try {
                extension.beforeAll(context);

                var server = extension.getEurekaServer();

                // Verify Register and Lookup works
                extension.registerApplication("foo", "bar", "baz", "UP");
                assertThat(server.getRegistry().getRegisteredApplication("FOO")).isNotNull();
                assertThat(extension.getRegisteredApplication("FOO"))
                        .usingRecursiveComparison()
                        .isEqualTo(server.getRegistry().getRegisteredApplication("FOO"));

                // Verify get all registered works
                assertThat(extension.getRegisteredApplications()).hasSameSizeAs(server.getRegistry().registeredApplications());

                // Verify is registered works
                assertThat(extension.isApplicationRegistered("FOO")).isTrue();

                // Verify heart beat count works
                assertThat(extension.getHeartbeatCount()).isEqualTo(server.getRegistry().getHeartbeatCount());
            } finally {
                extension.getEurekaServer().stop();
            }
        }
    }
}
