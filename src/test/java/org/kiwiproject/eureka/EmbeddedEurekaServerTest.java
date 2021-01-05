package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

@DisplayName("EmbeddedEurekaServer")
@SetSystemProperty(key = "eureka.numberRegistrySyncRetries", value = "0")
class EmbeddedEurekaServerTest {

    @Test
    void shouldStartWithRandomPort() {
        var server = new EmbeddedEurekaServer();

        try {
            server.start();

            assertThat(server.getEurekaPort()).isPositive();
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldStartWithGivenPort() {
        var server = new EmbeddedEurekaServer(8761);

        try {
            server.start();

            assertThat(server.getEurekaPort()).isEqualTo(8761);
        } finally {
            server.stop();
        }
    }
}
