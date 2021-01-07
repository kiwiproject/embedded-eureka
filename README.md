### Embedded Eureka
[![Build](https://github.com/kiwiproject/embedded-eureka/workflows/build/badge.svg)](https://github.com/kiwiproject/embedded-eureka/actions?query=workflow%3Abuild)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_embedded-eureka&metric=alert_status)](https://sonarcloud.io/dashboard?id=kiwiproject_embedded-eureka)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_embedded-eureka&metric=coverage)](https://sonarcloud.io/dashboard?id=kiwiproject_embedded-eureka)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/embedded-eureka/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/embedded-eureka)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/embedded-eureka)](https://search.maven.org/search?q=g:org.kiwiproject%20a:embedded-eureka)

An embeddable Eureka server to be used for testing. 

THIS SHOULD NOT BE USED IN PRODUCTION!!!

This version is based off of the v2 endpoints in Eureka.

#### How to use it
* Add the Maven dependency (available in Maven Central)

```xml
<dependency>
    <groupId>org.kiwiproject</groupId>
    <artifactId>embedded-eureka</artifactId>
    <version>[current-version]</version>
</dependency>
```

#### Junit 5

The extension can be added to a test class as follows:

```java
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.eureka.junit.EurekaServerExtension;

class ServiceTest {
    @RegisterExtension
    private static EurekaServerExtension EUREKA = new EurekaServerExtension();
    
    // Test code goes here
}
```

You can then get the port from the extension to pass into your Eureka client.
