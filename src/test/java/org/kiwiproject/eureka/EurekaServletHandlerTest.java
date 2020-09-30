package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.UUIDs;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DisplayName("EurekaServletHandler")
class EurekaServletHandlerTest {

    private static final String APP_NAME = "test-service";

    private static Server server;
    private static EurekaServletHandler handler;
    private static EurekaServer eurekaServer;

    private Client client;

    @BeforeAll
    static void startServer() throws Exception {
        eurekaServer = mock(EurekaServer.class);
        handler = new EurekaServletHandler(eurekaServer);

        var holder = new ServletHolder("eurekaHandler", handler);

        var handler = new ServletHandler();
        handler.addServletWithMapping(holder, "/eureka/v2/*");

        server = new Server(0);
        server.setHandler(handler);
        server.start();
    }

    @BeforeEach
    void setUpClient() {
        client = ClientBuilder.newClient();
    }

    @AfterEach
    void resetMocks() {
        reset(eurekaServer);
        handler.registrationWaitRetries.clear();
    }

    @AfterAll
    static void tearDown() throws Exception {
        server.stop();
    }

    @Nested
    class GetInstanceForAppsPath {

        @Test
        void shouldReturn404WhenInstanceNotFound() {
            var instanceId = UUIDs.randomUUIDString();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.empty());

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertNotFoundResponse(response);
        }

        @Test
        void shouldReturn404WhenMissingPathParams() {
            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}")
                    .resolveTemplate("appId", APP_NAME)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertNotFoundResponse(response);

            var responseString = response.readEntity(new GenericType<Map<String, String>>(){}).get("message");
            assertThat(responseString).isEqualTo("Request path: /apps/" + APP_NAME + " not supported by eureka mock.");
        }

        @Test
        void shouldReturnInstanceWhenFound() {
            var instanceId = UUIDs.randomUUIDString();

            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId(instanceId)
                    .build();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.of(instance));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertOkResponse(response);

            var instanceMap = response.readEntity(new GenericType<Map<String, InstanceInfo>>(){});
            assertThat(instanceMap.get("instance").getInstanceId()).isEqualTo(instanceId);
            assertThat(instanceMap.get("instance").getAppName()).isEqualToIgnoringCase(APP_NAME);
        }

        @Test
        void shouldReturn500WhenFailAwaitRegistrationFirstNTimesIsUsed() {
            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId("FailAwaitRegistrationFirstNTimes-1")
                    .build();

            when(eurekaServer.getInstance(APP_NAME, "FailAwaitRegistrationFirstNTimes-1")).thenReturn(Optional.of(instance));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", "FailAwaitRegistrationFirstNTimes-1")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertInternalServerErrorResponse(response);
        }

        @Test
        void shouldReturn500WhenFailAwaitRegistrationFirstNTimesIsUsed_ForNTimesThen200() {
            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId("FailAwaitRegistrationFirstNTimes-2")
                    .build();

            when(eurekaServer.getInstance(APP_NAME, "FailAwaitRegistrationFirstNTimes-2"))
                    .thenReturn(Optional.of(instance))
                    .thenReturn(Optional.of(instance))
                    .thenReturn(Optional.of(instance));

            for (var i = 0; i < 2; i++) {
                var failureResponse = client.target(server.getURI())
                        .path("/eureka/v2/apps/{appId}/{instanceId}")
                        .resolveTemplate("appId", APP_NAME)
                        .resolveTemplate("instanceId", "FailAwaitRegistrationFirstNTimes-2")
                        .request(MediaType.APPLICATION_JSON_TYPE)
                        .get();

                assertInternalServerErrorResponse(failureResponse);
            }

            var successfulResponse = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", "FailAwaitRegistrationFirstNTimes-2")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertOkResponse(successfulResponse);
        }
    }

    @Nested
    class GetApplicationsForVip {

        @SuppressWarnings("unchecked")
        @Test
        void shouldReturnAnEmptyApplicationWhenNoVipsMatch() {
            when(eurekaServer.applicationsThatMatchVipAddressFromPath(APP_NAME)).thenReturn(List.of());

            var response = client.target(server.getURI())
                    .path("/eureka/v2/vips/{vipAddress}")
                    .resolveTemplate("vipAddress", APP_NAME)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertOkResponse(response);

            var applicationMap = response.readEntity(new GenericType<Map<String, Map<String, Object>>>(){});

            assertThat(applicationMap).isNotNull();
            assertThat((List<Map<String, Object>>)applicationMap.get("applications").get("application")).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldReturnMatchingApplicationsWhenVipsMatch() {
            var app = new Application(APP_NAME);

            when(eurekaServer.applicationsThatMatchVipAddressFromPath("/vips/" + APP_NAME)).thenReturn(List.of(app));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/vips/{vipAddress}")
                    .resolveTemplate("vipAddress", APP_NAME)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertOkResponse(response);

            var applicationMap = response.readEntity(new GenericType<Map<String, Map<String, Object>>>(){});

            System.out.println(applicationMap);
            assertThat(applicationMap).isNotNull();
            assertThat((List<Map<String, Object>>)applicationMap.get("applications").get("application")).hasSize(1);
        }
    }
}
