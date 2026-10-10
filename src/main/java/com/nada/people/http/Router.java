package com.nada.people.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nada.people.domain.PersonNotFoundException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal method + path router on top of the JDK HTTP server.
 *
 * <p>Routes are matched against the request path using regular expressions; capture
 * groups are passed to the handler. Handlers return the response body (serialised to
 * JSON) or {@link Response#noContent()}. Exceptions are translated to JSON errors here, so
 * handlers never deal with status codes for failure cases.
 */
public class Router implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    /** Handles a matched request; {@code pathParams} are the regex capture groups. */
    @FunctionalInterface
    public interface Handler {
        Response handle(HttpExchange exchange, List<String> pathParams) throws IOException;
    }

    /** A successful response: a status and an optional body to serialise as JSON. */
    public record Response(int status, Object body) {
        public static Response ok(Object body) {
            return new Response(200, body);
        }

        public static Response created(Object body) {
            return new Response(201, body);
        }

        public static Response noContent() {
            return new Response(204, null);
        }
    }

    private record Route(String method, Pattern pattern, Handler handler) {
    }

    private final List<Route> routes = new ArrayList<>();
    private final ObjectMapper mapper;

    public Router(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Router add(String method, String pathRegex, Handler handler) {
        routes.add(new Route(method, Pattern.compile(pathRegex), handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Response response = dispatch(exchange);
            send(exchange, response.status(), response.body());
        } catch (ApiException e) {
            sendError(exchange, e.status(), e.getMessage());
        } catch (PersonNotFoundException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unhandled error for " + exchange.getRequestURI(), e);
            sendError(exchange, 500, "internal server error");
        } finally {
            exchange.close();
        }
    }

    private Response dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        boolean pathMatched = false;

        for (Route route : routes) {
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            pathMatched = true;
            if (route.method().equals(method)) {
                List<String> params = new ArrayList<>();
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    params.add(matcher.group(i));
                }
                return route.handler().handle(exchange, params);
            }
        }

        if (pathMatched) {
            throw new ApiException(405, "method " + method + " not allowed on " + path);
        }
        throw ApiException.notFound("no route for " + method + " " + path);
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, Map.of("error", message));
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        drain(exchange.getRequestBody());
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Consumes any unread request body so the connection can be reused. */
    private static void drain(InputStream in) throws IOException {
        in.transferTo(OutputStream.nullOutputStream());
    }
}
