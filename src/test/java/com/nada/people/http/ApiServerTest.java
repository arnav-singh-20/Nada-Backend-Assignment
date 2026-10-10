package com.nada.people.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests: each test starts a real server on a free port with an empty store. */
class ApiServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private ApiServer server;

    record Reply(int status, String body) {
        JsonNode json() throws IOException {
            return JSON.readTree(body);
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        server = ApiServer.start(0);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // ---- helpers ---------------------------------------------------------------------

    private Reply send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), response.body());
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + server.port() + pathAndQuery);
    }

    private Reply post(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    private Reply get(String pathAndQuery) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(pathAndQuery)).GET());
    }

    private long createPerson(String name) throws IOException, InterruptedException {
        Reply reply = post("/people", JSON.createObjectNode().put("name", name).toString());
        assertEquals(201, reply.status(), reply.body());
        return reply.json().get("id").asLong();
    }

    private void knows(long a, long b) throws IOException, InterruptedException {
        Reply reply = post("/knows", "{\"a\": " + a + ", \"b\": " + b + "}");
        assertEquals(204, reply.status(), reply.body());
    }

    /** Creates people and connects them in a simple chain, returning their IDs in order. */
    private List<Long> chain(String... names) throws IOException, InterruptedException {
        List<Long> ids = new ArrayList<>();
        for (String name : names) {
            ids.add(createPerson(name));
        }
        for (int i = 0; i + 1 < ids.size(); i++) {
            knows(ids.get(i), ids.get(i + 1));
        }
        return ids;
    }

    private static void assertError(Reply reply, int status) throws IOException {
        assertEquals(status, reply.status(), reply.body());
        assertTrue(reply.json().get("error").isTextual(), reply.body());
    }

    // ---- POST /people ------------------------------------------------------------------

    @Nested
    class CreatePerson {

        @Test
        void returns201WithAssignedId() throws Exception {
            Reply first = post("/people", "{\"name\": \"Asha\"}");
            Reply second = post("/people", "{\"name\": \"Rohan\"}");

            assertEquals(201, first.status());
            assertEquals(JSON.readTree("{\"id\": 1, \"name\": \"Asha\"}"), first.json());
            assertEquals(JSON.readTree("{\"id\": 2, \"name\": \"Rohan\"}"), second.json());
        }

        @ParameterizedTest
        @ValueSource(strings = {"आशा", "রোহন", "மீரா", "  Kabir  ", "Zoë 👩‍👩‍👧"})
        void returnsNameExactlyAsSupplied(String name) throws Exception {
            Reply reply = post("/people", JSON.createObjectNode().put("name", name).toString());

            assertEquals(201, reply.status());
            assertEquals(name, reply.json().get("name").textValue());
            long id = reply.json().get("id").asLong();
            long other = createPerson("Other");
            knows(id, other);
            assertEquals(name, get("/people/" + other + "/contacts").json().get(0).get("name").textValue());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "not json",
                "{\"name\": \"Asha\"",
                "{\"name\": \"Asha\"} trailing",
                "[]",
                "\"Asha\"",
                "{}",
                "{\"nme\": \"Asha\"}",
                "{\"name\": null}",
                "{\"name\": \"\"}",
                "{\"name\": \"   \"}",
                "{\"name\": \"\\t\\n\"}",
                "{\"name\": 42}",
                "{\"name\": [\"Asha\"]}",
        })
        void rejectsBadBodies(String body) throws Exception {
            assertError(post("/people", body), 400);
        }
    }

    // ---- POST /knows -------------------------------------------------------------------

    @Nested
    class Knows {

        @Test
        void returns204AndIsMutual() throws Exception {
            long asha = createPerson("Asha");
            long rohan = createPerson("Rohan");

            Reply reply = post("/knows", "{\"a\": " + asha + ", \"b\": " + rohan + "}");

            assertEquals(204, reply.status());
            assertEquals("", reply.body());
            assertEquals(JSON.readTree("[{\"id\": 2, \"name\": \"Rohan\"}]"), get("/people/1/contacts").json());
            assertEquals(JSON.readTree("[{\"id\": 1, \"name\": \"Asha\"}]"), get("/people/2/contacts").json());
        }

        @Test
        void repeatingAConnectionIsNotAnError() throws Exception {
            long asha = createPerson("Asha");
            long rohan = createPerson("Rohan");

            knows(asha, rohan);
            knows(asha, rohan);
            knows(rohan, asha);

            assertEquals(1, get("/people/" + asha + "/contacts").json().size());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "not json",
                "{}",
                "{\"a\": 1}",
                "{\"b\": 2}",
                "{\"a\": null, \"b\": 2}",
                "{\"a\": \"1\", \"b\": 2}",
                "{\"a\": 1.5, \"b\": 2}",
                "{\"a\": 1, \"b\": 99999999999999999999999}",
                "{\"a\": 1, \"b\": 1}",
        })
        void rejectsBadBodies(String body) throws Exception {
            createPerson("Asha");
            createPerson("Rohan");

            assertError(post("/knows", body), 400);
        }

        @Test
        void unknownPersonIs404() throws Exception {
            long asha = createPerson("Asha");

            Reply reply = post("/knows", "{\"a\": " + asha + ", \"b\": 9}");

            assertEquals(404, reply.status());
            assertEquals(JSON.readTree("{\"error\": \"no person 9\"}"), reply.json());
            assertEquals(0, get("/people/" + asha + "/contacts").json().size());
        }
    }

    // ---- GET /people/{id}/contacts ------------------------------------------------------

    @Nested
    class Contacts {

        @Test
        void emptyListForPersonWithoutContacts() throws Exception {
            long asha = createPerson("Asha");

            Reply reply = get("/people/" + asha + "/contacts");

            assertEquals(200, reply.status());
            assertEquals(JSON.readTree("[]"), reply.json());
        }

        @Test
        void listsOnlyDirectContacts() throws Exception {
            List<Long> ids = chain("Asha", "Rohan", "Meera");

            Reply reply = get("/people/" + ids.get(1) + "/contacts");

            assertEquals(200, reply.status());
            assertEquals(JSON.readTree("[{\"id\": 1, \"name\": \"Asha\"}, {\"id\": 3, \"name\": \"Meera\"}]"),
                    reply.json());
            assertEquals(1, get("/people/" + ids.get(0) + "/contacts").json().size());
        }

        @Test
        void unknownPersonIs404() throws Exception {
            Reply reply = get("/people/9/contacts");

            assertEquals(404, reply.status());
            assertEquals(JSON.readTree("{\"error\": \"no person 9\"}"), reply.json());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
        void malformedIdIs400(String id) throws Exception {
            assertError(get("/people/" + id + "/contacts"), 400);
        }
    }

    // ---- GET /path ---------------------------------------------------------------------

    @Nested
    class Path {

        /** Required test: A - B - C - D, A to D is connected in exactly three hops. */
        @Test
        void threeHopsIsConnected() throws Exception {
            List<Long> ids = chain("A", "B", "C", "D");

            Reply reply = get("/path?from=" + ids.get(0) + "&to=" + ids.get(3));

            assertEquals(200, reply.status());
            assertEquals(JSON.readTree("""
                    {"connected": true, "hops": 3, "path": [
                      {"id": 1, "name": "A"},
                      {"id": 2, "name": "B"},
                      {"id": 3, "name": "C"},
                      {"id": 4, "name": "D"}
                    ]}"""), reply.json());
        }

        /** Required test: A - B - C - D - E, A to E is four hops, which is beyond the limit. */
        @Test
        void fourHopsIsNotConnected() throws Exception {
            List<Long> ids = chain("A", "B", "C", "D", "E");

            Reply reply = get("/path?from=" + ids.get(0) + "&to=" + ids.get(4));

            assertEquals(200, reply.status());
            assertEquals(JSON.readTree("{\"connected\": false}"), reply.json());
        }

        @Test
        void returnsShortestPath() throws Exception {
            // Asha - Rohan - Meera - Kabir, plus a shortcut Asha - Dev - Kabir
            List<Long> ids = chain("Asha", "Rohan", "Meera", "Kabir");
            long dev = createPerson("Dev");
            knows(ids.get(0), dev);
            knows(dev, ids.get(3));

            JsonNode body = get("/path?from=" + ids.get(0) + "&to=" + ids.get(3)).json();

            assertEquals(2, body.get("hops").asInt());
            assertEquals(JSON.readTree("""
                    [{"id": 1, "name": "Asha"}, {"id": 5, "name": "Dev"}, {"id": 4, "name": "Kabir"}]"""),
                    body.get("path"));
        }

        @Test
        void samePersonIsZeroHops() throws Exception {
            long asha = createPerson("Asha");

            Reply reply = get("/path?from=" + asha + "&to=" + asha);

            assertEquals(200, reply.status());
            assertEquals(JSON.readTree("{\"connected\": true, \"hops\": 0, \"path\": [{\"id\": 1, \"name\": \"Asha\"}]}"),
                    reply.json());
        }

        @Test
        void unlinkedPeopleAreNotConnected() throws Exception {
            long asha = createPerson("Asha");
            long rohan = createPerson("Rohan");

            assertEquals(JSON.readTree("{\"connected\": false}"),
                    get("/path?from=" + asha + "&to=" + rohan).json());
        }

        @Test
        void unknownPersonIs404() throws Exception {
            long asha = createPerson("Asha");

            Reply unknownTo = get("/path?from=" + asha + "&to=9");
            Reply unknownFrom = get("/path?from=9&to=" + asha);
            Reply unknownSame = get("/path?from=9&to=9");

            assertEquals(JSON.readTree("{\"error\": \"no person 9\"}"), unknownTo.json());
            assertEquals(404, unknownTo.status());
            assertError(unknownFrom, 404);
            assertError(unknownSame, 404);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/path",
                "/path?from=1",
                "/path?to=1",
                "/path?from=&to=1",
                "/path?from=1&to=",
                "/path?from=abc&to=1",
                "/path?from=1&to=2x",
                "/path?from=1.0&to=1",
                "/path?from=%20&to=1",
        })
        void missingOrMalformedParamsAre400(String pathAndQuery) throws Exception {
            createPerson("Asha");

            assertError(get(pathAndQuery), 400);
        }
    }

    // ---- routing -----------------------------------------------------------------------

    @Test
    void unknownRouteIs404() throws Exception {
        assertError(get("/nope"), 404);
    }

    @Test
    void wrongMethodIs405() throws Exception {
        assertError(get("/people"), 405);
        assertError(post("/path", "{}"), 405);
    }
}
