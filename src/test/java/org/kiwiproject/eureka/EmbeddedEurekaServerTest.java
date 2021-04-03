package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
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

    @Test
    void shouldThrowIllegalState_WhenErrorStarting() {
        var embeddedEurekaServer = new ExceptionWhenStartingEmbeddedEurekaServer();

        assertThatIllegalStateException()
                .isThrownBy(embeddedEurekaServer::start)
                .withMessage("Eureka has not been started");
    }

    static class ExceptionWhenStartingEmbeddedEurekaServer extends EmbeddedEurekaServer {

        @Override
        Server newJettyServer() {
            return new Server() {
                @Override
                protected void doStart() throws Exception {
                    throw new Exception("error starting");
                }
            };
        }
    }

    @Test
    void shouldIgnoreRequest_ToStop_WhenAlreadyStopped() {
        var embeddedEurekaServer = new EmbeddedEurekaServer();
        embeddedEurekaServer.start();
        embeddedEurekaServer.stop();

        assertThat(embeddedEurekaServer.isStopped()).isTrue();

        assertThatCode(embeddedEurekaServer::stop).doesNotThrowAnyException();
        assertThatCode(embeddedEurekaServer::stop).doesNotThrowAnyException();
        assertThatCode(embeddedEurekaServer::stop).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowException_WhenExceptionStoppingServer() {
        var embeddedEurekaServer = new ExceptionWhenStoppingEmbeddedEurekaServer();
        embeddedEurekaServer.start();

        assertThatCode(embeddedEurekaServer::stop).doesNotThrowAnyException();
    }

    static class ExceptionWhenStoppingEmbeddedEurekaServer extends EmbeddedEurekaServer {

        @Override
        Server newJettyServer() {
            return new Server() {
                @Override
                protected void doStop() throws Exception {
                    throw new Exception("error stopping");
                }
            };
        }
    }

    @Test
    void shouldNotThrowException_WhenInterruptedStoppingServer() {
        var embeddedEurekaServer = new InterruptedWhenStoppingEmbeddedEurekaServer();
        embeddedEurekaServer.start();

        assertThatCode(embeddedEurekaServer::stop).doesNotThrowAnyException();
    }

    static class InterruptedWhenStoppingEmbeddedEurekaServer extends EmbeddedEurekaServer {

        @Override
        Server newJettyServer() {
            return new Server() {
                @Override
                protected void doStop() throws Exception {
                    throw new InterruptedException("interrupt!");
                }
            };
        }
    }
}
