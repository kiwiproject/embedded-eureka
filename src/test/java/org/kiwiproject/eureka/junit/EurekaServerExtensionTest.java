package org.kiwiproject.eureka.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

@DisplayName("EurekaServerExtension")
class EurekaServerExtensionTest {

    @Nested
    class BeforeAll {

        @Test
        void shouldCreateTheEurekaServerInstanceAndStartARealServer() {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            extension.beforeAll(context);

            var server = extension.getEmbeddedEurekaServer();

            assertThat(server).isNotNull();
            assertThat(server.isStarted()).isTrue();
            assertThat(extension.getPort()).isPositive();

            server.stop();
        }

    }

    @Nested
    class AfterAll {

        @Test
        void shouldStopTheServer() {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            extension.beforeAll(context);
            extension.afterAll(context);

            var server = extension.getEmbeddedEurekaServer();

            assertThat(server).isNotNull();
            assertThat(server.isStarted()).isFalse();
        }

    }
}
