package com.nada.people.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nada.people.domain.Person;
import com.nada.people.service.PathFinder;
import com.nada.people.store.PeopleStore;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.nada.people.http.RequestParsing.parseId;
import static com.nada.people.http.RequestParsing.parseQuery;
import static com.nada.people.http.RequestParsing.readJsonObject;
import static com.nada.people.http.RequestParsing.requireId;
import static com.nada.people.http.RequestParsing.requireNonBlankString;
import static com.nada.people.http.Router.Response;

/** HTTP handlers for the people and connections endpoints. */
public class PeopleApi {

    /** Body of {@code GET /path}. {@code hops} and {@code path} are omitted when not connected. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PathResponse(boolean connected, Integer hops, List<Person> path) {
        static PathResponse found(List<Person> path) {
            return new PathResponse(true, path.size() - 1, path);
        }

        static PathResponse notConnected() {
            return new PathResponse(false, null, null);
        }
    }

    private final PeopleStore store;
    private final PathFinder pathFinder;
    private final ObjectMapper mapper;

    public PeopleApi(PeopleStore store, PathFinder pathFinder, ObjectMapper mapper) {
        this.store = store;
        this.pathFinder = pathFinder;
        this.mapper = mapper;
    }

    public Router router() {
        return new Router(mapper)
                .add("POST", "/people", (exchange, params) -> createPerson(exchange))
                .add("GET", "/people/([^/]+)/contacts", (exchange, params) -> contacts(params.get(0)))
                .add("POST", "/knows", (exchange, params) -> connect(exchange))
                .add("GET", "/path", (exchange, params) -> path(exchange));
    }

    private Response createPerson(HttpExchange exchange) throws IOException {
        JsonNode body = readJsonObject(mapper, exchange.getRequestBody());
        String name = requireNonBlankString(body, "name");
        return Response.created(store.create(name));
    }

    private Response connect(HttpExchange exchange) throws IOException {
        JsonNode body = readJsonObject(mapper, exchange.getRequestBody());
        long a = requireId(body, "a");
        long b = requireId(body, "b");
        if (a == b) {
            throw ApiException.badRequest("a person cannot know themselves (a == b == " + a + ")");
        }
        store.connect(a, b);
        return Response.noContent();
    }

    private Response contacts(String rawId) {
        long id = parseId(rawId, "id");
        return Response.ok(store.contactsOf(id));
    }

    private Response path(HttpExchange exchange) {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        long from = parseId(query.get("from"), "from");
        long to = parseId(query.get("to"), "to");
        return Response.ok(pathFinder.shortestPath(from, to)
                .map(PathResponse::found)
                .orElseGet(PathResponse::notConnected));
    }
}
