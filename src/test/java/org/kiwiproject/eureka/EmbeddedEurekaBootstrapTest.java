package org.kiwiproject.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.eureka.junit.EurekaServerExtension;

@DisplayName("EmbeddedEurekaBootstrap")
class EmbeddedEurekaBootstrapTest {

    @RegisterExtension
    public static final EurekaServerExtension EUREKA = new EurekaServerExtension();

    @AfterEach
    void cleanupEureka() {
        EUREKA.getEurekaServer().getRegistry().cleanupApps();
    }

    @Nested
    class RegisteredApplications {

        @Test
        void shouldReturnEmptyList_WhenNothingRegistered() {
            assertThat(EUREKA.getEurekaServer().getRegistry().registeredApplications()).isEmpty();
        }

        @Test
        void shouldReturnListWithRegisteredApplications() {
            EUREKA.getEurekaServer().getRegistry().registerApplication("APPID", "INSTANCEID", "VIP", "UP");

            assertThat(EUREKA.getEurekaServer().getRegistry().registeredApplications()).extracting("name").containsOnly("APPID");
        }
    }

    @Nested
    class IsApplicationRegistered {

        @Test
        void shouldReturnFalse_WhenNotRegistered() {
            assertThat(EUREKA.getEurekaServer().getRegistry().isApplicationRegistered("APPID")).isFalse();
        }

        @Test
        void shouldReturnTrue_WhenRegistered() {
            EUREKA.getEurekaServer().getRegistry().registerApplication("APPID", "INSTANCEID", "VIP", "UP");
            assertThat(EUREKA.getEurekaServer().getRegistry().isApplicationRegistered("APPID")).isTrue();
        }
    }

    @Nested
    class GetRegisteredApplication {

        @Test
        void shouldReturnNull_WhenNotRegistered() {
            assertThat(EUREKA.getEurekaServer().getRegistry().getRegisteredApplication("APPID")).isNull();
        }

        @Test
        void shouldReturnApplication_WhenRegistered() {
            EUREKA.getEurekaServer().getRegistry().registerApplication("APPID", "INSTANCEID", "VIP", "UP");
            assertThat(EUREKA.getEurekaServer().getRegistry().getRegisteredApplication("APPID")).isNotNull();
        }
    }

    @Nested
    class GetHeartbeatCount {

        @Test
        void shouldReturnZero_WhenNoHeartbeatsSent() {
            assertThat(EUREKA.getEurekaServer().getRegistry().getHeartbeatCount()).isZero();
        }
    }
}
