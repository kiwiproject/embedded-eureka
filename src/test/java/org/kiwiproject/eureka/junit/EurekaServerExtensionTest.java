package org.kiwiproject.eureka.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.eureka.EurekaServletHandler;

@DisplayName("EurekaServerExtension")
class EurekaServerExtensionTest {

    @Nested
    class BeforeAll {

        @Test
        void shouldCreateTheEurekaServerInstanceAndStartARealServer() throws Exception {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            extension.beforeAll(context);

            var server = extension.getServer();

            assertThat(server).isNotNull();
            assertThat(server.isStarted()).isTrue();
            assertThat(extension.getPort()).isEqualTo(((ServerConnector) server.getConnectors()[0]).getLocalPort());
            assertThat(extension.getEurekaServer()).isNotNull();

            var handler = ((ServletHandler) extension.getServer().getHandler());
            assertThat(handler.getServlets()[0].getHeldClass()).isEqualTo(EurekaServletHandler.class);

            extension.getServer().stop();
        }

    }

    @Nested
    class AfterAll {

        @Test
        void shouldStopTheServer() throws Exception {
            var extension = new EurekaServerExtension();
            var context = mock(ExtensionContext.class);

            extension.beforeAll(context);
            extension.afterAll(context);

            var server = extension.getServer();

            assertThat(server).isNotNull();
            assertThat(server.isStopped()).isTrue();
        }

    }
}
