package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.net.KiwiUrls;

import javax.ws.rs.client.ClientBuilder;

@Slf4j
@DisplayName("EmbeddedEurekaServer")
class EmbeddedEurekaServerTest {

    @BeforeEach
    void setUp() {
        EurekaTestHelpers.resetStatsMonitor();
    }

    @Test
    void shouldStartWithRandomPort() {
        var server = new EmbeddedEurekaServer();
        try {
            server.start();

            assertThat(server.getEurekaPort()).isPositive();
            assertThat(server.isStarted()).isTrue();

            var client = ClientBuilder.newClient();

            var url = KiwiUrls.createHttpUrl("localhost", server.getEurekaPort());
            var response = client.target(url)
                    .path("/v2/apps")
                    .request()
                    .get();

            assertOkResponse(response);
        } finally {
            server.stop();
        }
    }

}
