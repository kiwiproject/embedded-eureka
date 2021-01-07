package org.kiwiproject.eureka;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.eureka.Jersey2EurekaBootStrap;

// TODO: Add javadoc
// TODO: Can this be package private?  People will get one of these in tests, but don't need to create one.
public class EmbeddedEurekaBootstrap extends Jersey2EurekaBootStrap {

    public EmbeddedEurekaBootstrap(DiscoveryClient client) {
        super(client);
    }

    // TODO: Add various calls to access/modify internal Eureka data :-)
}
