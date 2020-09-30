package org.kiwiproject.eureka;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.EurekaJacksonCodec;
import com.netflix.discovery.shared.Applications;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class EurekaServletHandler extends HttpServlet {

    private static final boolean REQUEST_HANDLED = true;
    private static final boolean REQUEST_NOT_HANDLED = false;

    @SuppressWarnings("java:S1075")
    private static final String APP_BASE_PATH = "/apps";

    @SuppressWarnings("java:S1075")
    private static final String VIP_BASE_PATH = "/vips";

    @VisibleForTesting
    final ConcurrentHashMap<String, Pair<Integer, Integer>> registrationWaitRetries = new ConcurrentHashMap<>();

    @VisibleForTesting
    private final ConcurrentHashMap<String, Pair<Integer, Integer>> heartbeatRetries = new ConcurrentHashMap<>();

    private final EurekaServer eurekaServer;

    public EurekaServletHandler(EurekaServer eurekaServer) {
        this.eurekaServer = eurekaServer;
    }

    /**
     * Handle GET requests to {@code /apps/{appId}/{instanceId}} or to {@code /vips/{vipAddress}}.
     * <p>
     * You can cause requests to {@code /apps/{appId}/{instanceId}} to return an HTTP 500 error N times by setting
     * the instanceId/hostName of the candidate to the literal string "FailAwaitRegistrationFirstNTimes-N", for
     * example FailAwaitRegistrationFirstNTimes-5 causes it to return a 500 error for the first 5 times a GET
     * to {@code /apps/someAppName/FailAwaitRegistrationFirstNTimes-5} is made.
     */
    @Override
    @SuppressWarnings({"java:S1075", "java:S1989"})
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var handlers = new HashMap<String, RequestHandler>();

        handlers.put(APP_BASE_PATH, getAppsGetHandler());
        handlers.put(VIP_BASE_PATH, getVipsGetHandler());

        handleRequest((Request) req, (Response) resp, handlers);
    }

    private RequestHandler getAppsGetHandler() {
        return (pathInfo, request, response) -> {
            var pathParts = splitPathInfo(pathInfo);
            var isInstanceRequest = pathParts.length == 3;

            if (isInstanceRequest) {
                var appId = pathParts[1];
                var instanceId = pathParts[2];

                int responseStatusCode = getResponseCodeWithPossibleRetryFailure(registrationWaitRetries,
                        appId, instanceId, "FailAwaitRegistrationFirstNTimes-", 200, 500);

                var instance = eurekaServer.getInstance(appId, instanceId).orElse(null);
                if (nonNull(instance)) {
                    var content = generateResponse(instance, getAcceptContentType(request));
                    sendResponseWithContent(request, response, responseStatusCode, content);
                } else {
                    sendNotFoundResponseForMissingInstanceInfo(request, response, appId, instanceId);
                }

                return REQUEST_HANDLED;
            }

            return REQUEST_NOT_HANDLED;
        };
    }

    private String[] splitPathInfo(String pathInfo) {
        return pathInfo.substring(1).split("/");
    }

    private RequestHandler getVipsGetHandler() {
        return (pathInfo, request, response) -> {
            LOG.trace("Received GET /vips request with pathInfo {}", pathInfo);

            var apps = new Applications();
            apps.setAppsHashCode(apps.getReconcileHashCode());

            // only add apps that match the VIP address in the path
            eurekaServer.applicationsThatMatchVipAddressFromPath(pathInfo).forEach(apps::addApplication);

            var content = generateResponse(apps, getAcceptContentType(request));
            sendOkResponseWithContent(request, response, content);
            return REQUEST_HANDLED;
        };
    }

    private void handleRequest(Request request, Response response, Map<String, RequestHandler> handlers) throws IOException {
        var pathInfo = request.getPathInfo();
        LOG.debug("Eureka mock received request on path: {}. Accept: |{}|. HTTP method: |{}|. Params: |{}|",
                request.getPathInfo(), getAcceptContentType(request), request.getMethod(), request.getQueryString());

        var handled = false;
        for (Map.Entry<String, RequestHandler> handler : handlers.entrySet()) {
            if (pathInfo.startsWith(handler.getKey())) {
                handled = handler.getValue().run(pathInfo, request, response);
                if (handled) {
                    break;
                }
            }
        }

        if (!handled) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Request path: " + pathInfo + " not supported by eureka mock.");
        }
    }

    /**
     * If {@code checkValue} starts with the specified {@code triggerValue}, then either insert or update
     * an entry in the {@code retries} map using the key {@code retryKey}.
     * <p>
     * The {@code retries} map contains values that are pairs of integers, the left value being the number of times
     * to fail if called multiple times, and the right value being the number of times it has been retried.
     * <p>
     * Based on the number of times failed and the number of times we want to fail before returning success, set the
     * response code to either {@code successResponseCode} or {@code errorResponseCode}.
     */
    private int getResponseCodeWithPossibleRetryFailure(ConcurrentHashMap<String, Pair<Integer, Integer>> retries,
                                                        String retryKey,
                                                        String checkValue,
                                                        String triggerValue,
                                                        int successResponseCode,
                                                        int errorResponseCode) {
        var responseStatusCode = successResponseCode;

        if (checkValue.startsWith(triggerValue)) {
            LOG.debug("Got trigger value {} for check value {}", triggerValue, checkValue);

            if (retries.containsKey(retryKey)) {
                var failureInfo = retries.get(retryKey);
                var numTimesToFail = failureInfo.getLeft();
                var numTimesFailed = failureInfo.getRight();

                if (numTimesFailed < numTimesToFail) {
                    LOG.debug("{} times failed is less than num times to fail {} for retry key {}, returning error code {}",
                            numTimesFailed, numTimesToFail, retryKey, errorResponseCode);
                    responseStatusCode = errorResponseCode;
                    retries.replace(retryKey, failureInfo, Pair.of(numTimesToFail, ++numTimesFailed));
                } else {
                    LOG.debug("Reached num times to fail {} for retry key {}, returning success code {}",
                            numTimesToFail, retryKey, successResponseCode);
                    responseStatusCode = HttpServletResponse.SC_OK;
                    retries.remove(retryKey);
                }
            } else { // first attempt, setup in map
                responseStatusCode = errorResponseCode;
                var numTimesToFail = getResponseCodeFromTriggerValue(checkValue);
                var numTimesFailed = 1;
                LOG.debug("Set up retry with key {} with {} times to fail, and return error code {}",
                        retryKey, numTimesToFail, errorResponseCode);
                retries.put(retryKey, Pair.of(numTimesToFail, numTimesFailed));
            }
        }
        return responseStatusCode;
    }

    private int getResponseCodeFromTriggerValue(String value) {
        return Integer.parseInt(value.split("-")[1]);
    }

    private static void sendOkResponseWithContent(Request request, Response response, String content) throws IOException {
        sendResponseWithContent(request, response, HttpServletResponse.SC_OK, content);
    }

    private static void sendResponseWithContent(Request request, HttpServletResponse response, int statusCode, String content) throws IOException {
        response.setContentType(getAcceptContentType(request));
        response.setStatus(statusCode);
        response.getWriter().println(content);
        response.getWriter().flush();
        request.setHandled(true);
        LOG.debug("Eureka mock sent response with status [{}] for request path [{}] with content: {}",
                statusCode, request.getPathInfo(), content);
    }

    private static void sendNotFoundResponseForMissingInstanceInfo(Request request, Response resp, String appId, String instanceId) {
        LOG.trace("No InstanceInfo for {} / {}; sending 404 response with no content", appId, instanceId);
        sendNotFoundResponseWithNoContent(request, resp);
    }

    private static void sendNotFoundResponseWithNoContent(Request request, Response response) {
        response.setContentType(getAcceptContentType(request));
        response.setStatus(404);
        request.setHandled(true);
        LOG.debug("Eureka mock sent response with status [404] for request path [{}] with NO content", request.getPathInfo());
    }

    private static String getAcceptContentType(Request request) {
        return request.getHeader("Accept");
    }

    private static String generateResponse(Object raw, String acceptHeader) {
        if (acceptHeader.startsWith("application/json")) {
            if (raw == null) {
                return null;
            }
            return new EurekaJacksonCodec().writeToString(raw);
        } else {
            throw new IllegalArgumentException(acceptHeader + " is not allowed (use application/json)");
        }
    }

    /**
     * Handles PUT requests for either heartbeats or status updates.
     * <p>
     * To perform a heartbeat just do a PUT to /apps/{appId}/{instanceId}. You can cause heartbeats to fail with a
     * 404 by setting the hostName to "FailHeartbeat-N" where N is the number of times to fail. You can also specify
     * the response code by adding the response code you want under the key "FailHeartbeatResponseCode" in the
     * registration candidate's metadata map.
     * <p>
     * To do a status update, PUT to /apps/{appId}/{instanceId}/status?value=new-status. You can also cause a status
     * change to fail by setting the candidate instanceId/hostName to the literal string "FailStatusChange".
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var handlers = new HashMap<String, RequestHandler>();
        handlers.put(APP_BASE_PATH, getAppsPutHandler());
        handleRequest((Request) req, (Response) resp, handlers);
    }

    private RequestHandler getAppsPutHandler() {
        return (pathInfo, request, response) -> {
            var pathParts = splitPathInfo(pathInfo);
            var appId = pathParts[1];
            var instanceId = pathParts[2];
            var instance = eurekaServer.getInstance(appId, instanceId).orElse(null);

            if (isNull(instance)) {
                sendNotFoundResponseForMissingInstanceInfo(request, response, appId, instanceId);
                return REQUEST_HANDLED;
            }

            var isStatusChangeRequest = pathParts.length > 3 && "status".equals(pathParts[3]);

            int statusCode;
            if (isStatusChangeRequest) {
                statusCode = handleStatusChangeRequest(instance, request.getParameter("value"));
            } else {
                statusCode = handleHeartBeatRequest(instance);
            }

            var content = generateResponse("", getAcceptContentType(request));
            sendResponseWithContent(request, response, statusCode, content);
            return REQUEST_HANDLED;
        };
    }

    private int handleHeartBeatRequest(InstanceInfo existingInstance) {
        var appName = existingInstance.getAppName();
        var hostName = existingInstance.getHostName();
        var metadata = existingInstance.getMetadata();
        var errorStatusCode = HttpServletResponse.SC_NOT_FOUND;

        if (metadata.containsKey("FailHeartbeatResponseCode")) {
            errorStatusCode = Integer.parseInt(metadata.get("FailHeartbeatResponseCode"));
        }

        var statusCode = getResponseCodeWithPossibleRetryFailure(heartbeatRetries, appName,
                hostName, "FailHeartbeat-", HttpServletResponse.SC_OK, errorStatusCode);

        eurekaServer.updateHeartbeatFor(appName, hostName, statusCode, existingInstance.getStatus());

        LOG.debug("Returning {} on heartbeat request for app {}, instance {}", statusCode, appName, hostName);
        return statusCode;
    }



    int handleStatusChangeRequest(InstanceInfo existingInstance, String newStatus) {
        if ("FailStatusChange".equals(existingInstance.getHostName())) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        existingInstance.setStatus(InstanceInfo.InstanceStatus.valueOf(newStatus));
        return HttpServletResponse.SC_OK;
    }
}
