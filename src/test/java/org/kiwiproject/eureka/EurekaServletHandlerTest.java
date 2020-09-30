package org.kiwiproject.eureka;

import static javax.ws.rs.client.Entity.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNoContentResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

            assertThat(applicationMap).isNotNull();
            assertThat((List<Map<String, Object>>)applicationMap.get("applications").get("application")).hasSize(1);
        }
    }

    @Nested
    class PutHeartbeatsAndStatus {

        @Test
        void shouldReturn404IfInstanceCannotBeFoundForHeartbeat() {
            var instanceId = UUIDs.randomUUIDString();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.empty());

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertNotFoundResponse(response);
        }

        @Test
        void shouldReturn404IfInstanceCannotBeFoundForStatus() {
            var instanceId = UUIDs.randomUUIDString();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.empty());

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}/status")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertNotFoundResponse(response);
        }

        @Test
        void shouldUpdateHeartbeatAndReturn200() {
            var instanceId = UUIDs.randomUUIDString();

            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId(instanceId)
                    .setHostName("localhost")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .build();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.of(instance));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertOkResponse(response);
            verify(eurekaServer).updateHeartbeatFor(APP_NAME.toUpperCase(), "localhost", 200, InstanceInfo.InstanceStatus.UP);
        }

        @Test
        void shouldUpdateHeartbeatAndReturn500_OnTriggeredFailure() {
            var instanceId = UUIDs.randomUUIDString();

            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId(instanceId)
                    .setHostName("FailHeartbeat-2")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .setMetadata(Map.of("FailHeartbeatResponseCode", "500"))
                    .build();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.of(instance));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertInternalServerErrorResponse(response);
            verify(eurekaServer).updateHeartbeatFor(APP_NAME.toUpperCase(), "FailHeartbeat-2", 500, InstanceInfo.InstanceStatus.UP);
        }

        @Test
        void shouldUpdateHeartbeatAndReturn500_OnTriggeredFailure_ThenSuccessAfterTriggeredNumber() {
            var instanceId = UUIDs.randomUUIDString();

            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId(instanceId)
                    .setHostName("FailHeartbeat-2")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .setMetadata(Map.of("FailHeartbeatResponseCode", "500"))
                    .build();

            when(eurekaServer.getInstance(APP_NAME, instanceId))
                    .thenReturn(Optional.of(instance))
                    .thenReturn(Optional.of(instance))
                    .thenReturn(Optional.of(instance));


            for (var i = 0; i < 2; i++) {
                var response = client.target(server.getURI())
                        .path("/eureka/v2/apps/{appId}/{instanceId}")
                        .resolveTemplate("appId", APP_NAME)
                        .resolveTemplate("instanceId", instanceId)
                        .request(MediaType.APPLICATION_JSON_TYPE)
                        .put(json(""));

                assertInternalServerErrorResponse(response);
            }

            verify(eurekaServer, times(2)).updateHeartbeatFor(APP_NAME.toUpperCase(), "FailHeartbeat-2", 500, InstanceInfo.InstanceStatus.UP);

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertOkResponse(response);
            verify(eurekaServer).updateHeartbeatFor(APP_NAME.toUpperCase(), "FailHeartbeat-2", 200, InstanceInfo.InstanceStatus.UP);
        }

        @Test
        void shouldReturn200ForStatusOnSuccess() {
            var instanceId = UUIDs.randomUUIDString();

            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId(instanceId)
                    .setHostName("localhost")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .build();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.of(instance));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}/status")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .queryParam("value", "DOWN")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertOkResponse(response);
        }

        @Test
        void shouldReturn500ForStatusOnTriggeredFailure() {
            var instanceId = UUIDs.randomUUIDString();

            var instance = InstanceInfo.Builder.newBuilder()
                    .setAppName(APP_NAME)
                    .setVIPAddress(APP_NAME)
                    .setInstanceId(instanceId)
                    .setHostName("FailStatusChange")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .build();

            when(eurekaServer.getInstance(APP_NAME, instanceId)).thenReturn(Optional.of(instance));

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}/{instanceId}/status")
                    .resolveTemplate("appId", APP_NAME)
                    .resolveTemplate("instanceId", instanceId)
                    .queryParam("value", "DOWN")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(json(""));

            assertInternalServerErrorResponse(response);
        }
    }

    @Nested
    class PostRegistration {

        @Test
        void shouldRegisterApplicationAndReturn204() {
            var postBody = Map.of(
                    "instance", Map.of(
                            "hostName", "localhost",
                            "homePageUrl", "http://localhost",
                            "statusPageUrl", "http://localhost/status",
                            "healthCheckUrl", "http://localhost/healthcheck",
                            "status", "STARTING",
                            "port", Map.of("$", 8080),
                            "securePort", Map.of("$", 8081),
                            "vipAddress", APP_NAME
                    )
            );

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}")
                    .resolveTemplate("appId", APP_NAME)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(json(postBody));

            assertNoContentResponse(response);
            verify(eurekaServer).registerApplication(isA(InstanceInfo.class));
        }

        @Test
        void shouldAttemptToRegisterAndReturn500_OnTriggeredFailure() {
            var postBody = Map.of(
                    "instance", Map.of(
                            "hostName", "localhost",
                            "homePageUrl", "http://localhost",
                            "statusPageUrl", "http://localhost/status",
                            "healthCheckUrl", "http://localhost/healthcheck",
                            "status", "STARTING",
                            "port", Map.of("$", 8080),
                            "securePort", Map.of("$", 8081),
                            "vipAddress", "RegisterUseResponseStatusCode-500"
                    )
            );

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}")
                    .resolveTemplate("appId", APP_NAME)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(json(postBody));

            assertInternalServerErrorResponse(response);
            verifyNoInteractions(eurekaServer);
        }

        @Test
        void shouldAttemptToRegisterAndReturn500_OnTriggeredFailure_ThenSuccessAfterTriggeredNumber() {
            var postBody = Map.of(
                    "instance", Map.of(
                            "hostName", "localhost",
                            "homePageUrl", "http://localhost",
                            "statusPageUrl", "http://localhost/status",
                            "healthCheckUrl", "http://localhost/healthcheck",
                            "status", "STARTING",
                            "port", Map.of("$", 8080),
                            "securePort", Map.of("$", 8081),
                            "vipAddress", "FailRegistrationFirstNTimes-2"
                    )
            );

            for (var i = 0; i < 2; i++) {
                var response = client.target(server.getURI())
                        .path("/eureka/v2/apps/{appId}")
                        .resolveTemplate("appId", APP_NAME)
                        .request(MediaType.APPLICATION_JSON_TYPE)
                        .post(json(postBody));

                assertInternalServerErrorResponse(response);
            }

            var response = client.target(server.getURI())
                    .path("/eureka/v2/apps/{appId}")
                    .resolveTemplate("appId", APP_NAME)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(json(postBody));

            assertNoContentResponse(response);
            verify(eurekaServer).registerApplication(isA(InstanceInfo.class));
        }
    }
}
