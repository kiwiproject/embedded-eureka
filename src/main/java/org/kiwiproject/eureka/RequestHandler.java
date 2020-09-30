package org.kiwiproject.eureka;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.io.IOException;

public interface RequestHandler {

    boolean run(String pathInfo, Request req, Response resp) throws IOException;

}
