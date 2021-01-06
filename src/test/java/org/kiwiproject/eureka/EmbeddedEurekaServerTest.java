package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmbeddedEurekaServer")
class EmbeddedEurekaServerTest {

    @Test
    void shouldStartWithRandomPort() {
        var server = new EmbeddedEurekaServer();
        try {
            server.start();
            assertThat(server.getEurekaPort()).isPositive();
            assertThat(server.isStarted()).isTrue();

            // TODO: Once upgraded to Jersey 2 use jersey client to make sure an endpoint works
        } finally {
            server.stop();
        }
    }

}