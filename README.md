# People & Connections API

A small HTTP API for storing people and their direct contacts and for finding how two people are connected, up to three hops. All data is kept in memory.

- Java 17+ with the JDK's built-in `com.sun.net.httpserver` (no web framework)
- Jackson for JSON
- JUnit 5 for tests
- Maven, via the included wrapper (`./mvnw`), so no global Maven install is needed

## Requirements

- JDK 17 or newer on your `PATH` (`java -version`)
- Internet access on the first build, so the wrapper can download Maven and the dependencies

## Running the server

```bash
./mvnw package            # builds target/people-api.jar (also runs the tests)
java -jar target/people-api.jar
```

The server listens on **port 8080**. To use another port, set `PORT`:

```bash
PORT=9090 java -jar target/people-api.jar
```

To build without running the tests, use `./mvnw package -DskipTests`. On Windows, use `mvnw.cmd` in place of `./mvnw`.

### Example session

```bash
curl -i -X POST localhost:8080/people -H 'Content-Type: application/json' -d '{"name": "Asha"}'
# 201 {"id":1,"name":"Asha"}
curl -X POST localhost:8080/people -d '{"name": "रोहन"}'     # {"id":2,"name":"रोहन"}
curl -X POST localhost:8080/people -d '{"name": "Meera"}'    # {"id":3,"name":"Meera"}

curl -i -X POST localhost:8080/knows -d '{"a": 1, "b": 2}'   # 204
curl -i -X POST localhost:8080/knows -d '{"a": 2, "b": 3}'   # 204

curl localhost:8080/people/1/contacts                        # [{"id":2,"name":"रोहन"}]
curl 'localhost:8080/path?from=1&to=3'
# {"connected":true,"hops":2,"path":[{"id":1,"name":"Asha"},{"id":2,"name":"रोहन"},{"id":3,"name":"Meera"}]}
```

## Running the tests

```bash
./mvnw test
```

The suite has 72 tests:

| Test class | What it covers |
|---|---|
| `ApiServerTest` | End-to-end tests. Each one starts a real server on a free port and sends HTTP requests to it. This includes the **required path test**: `A-B-C-D` gives A to D connected in 3 hops, and `A-B-C-D-E` gives A to E not connected. It also covers validation, 404s, repeated connections and names in Hindi, Bengali, Tamil and emoji. |
| `PathFinderTest` | The search on its own: the hop limit in both directions, shortest path when a longer one also exists, cycles and unknown IDs. |
| `PeopleStoreTest` | ID assignment, mutual and idempotent connections, contact ordering. |

## API

| Method & path | Success | Errors |
|---|---|---|
| `POST /people` `{"name": "Asha"}` | `201` `{"id": 1, "name": "Asha"}` | `400` invalid JSON, body not an object, `name` missing, not a string, empty or blank |
| `POST /knows` `{"a": 1, "b": 2}` | `204`, no body. Repeating the same pair (in either order) also returns `204`. | `400` invalid JSON, `a`/`b` missing or not integers, `a == b`; `404` unknown person |
| `GET /people/{id}/contacts` | `200` list of direct contacts, sorted by ID, or `[]` | `400` malformed id; `404` unknown person |
| `GET /path?from=1&to=5` | `200` `{"connected": true, "hops": n, "path": [...]}` or `{"connected": false}` | `400` `from`/`to` missing or malformed; `404` unknown person |

Errors are JSON, e.g. `{"error": "no person 9"}`. Unknown routes return `404` and a wrong method on a known route returns `405`.

### Behaviour notes

- **Names are stored exactly as sent.** They are not trimmed or normalised, so `"  Kabir  "` comes back with its spaces. A name made only of whitespace is rejected as blank. Responses are UTF-8.
- **Shortest path:** a breadth-first search from `from`, which stops after three levels. A path of exactly 3 hops is returned; a path of 4 or more counts as `connected: false`. `from == to` returns `hops: 0` with a single-person path. The search visits contacts in ascending ID order, so when several shortest paths exist, the same one is returned every time.
- **Validation order:** the request shape is checked first (400) and the people's existence second (404). For example, `{"a": 9, "b": 9}` returns 400 even though person 9 does not exist.
- **IDs** must be JSON integers in bodies. `"1"` and `1.5` are rejected. In paths and query strings they must parse as a Java `long`.
- A query string with an invalid percent-escape (e.g. `?from=%zz`) is rejected by the JDK HTTP server with a 400 before it reaches the application, so that particular 400 has an HTML body instead of JSON.

## Project layout

```
src/main/java/com/nada/people/
├── App.java                     entry point, reads PORT
├── domain/                      Person record, PersonNotFoundException
├── store/PeopleStore.java       in-memory people + undirected adjacency sets
├── service/PathFinder.java      depth-limited BFS
└── http/
    ├── ApiServer.java           wires everything onto the JDK HttpServer
    ├── Router.java              method + regex routing, error → JSON mapping
    ├── PeopleApi.java           the four endpoint handlers
    ├── RequestParsing.java      body/param validation (throws 400s)
    └── ApiException.java
```

Handlers stay simple: they parse input, call the store or path finder, and return a value. All status-code mapping for errors happens in `Router`. `PeopleStore` uses `synchronized` methods so that the person map and the adjacency map always agree. Connections are never removed, so a path found while other requests are writing is still a valid path.

## What I would do next with more time

- **Bidirectional BFS** for `/path`. It explores roughly the square root of the nodes a one-sided BFS does, which matters once people have hundreds of contacts.
- **Persistence**, e.g. Postgres with an edges table, or a graph store if the queries grow beyond shortest path. I would put `PeopleStore` behind an interface first so the in-memory version stays available for tests.
- **Finer-grained concurrency.** For read-heavy traffic, use a `ReadWriteLock` or concurrent maps with copy-on-write contact sets in place of one monitor.
- **More endpoints:** `GET /people/{id}`, removing a connection, and listing *all* shortest paths, which is useful for showing families every mutual contact.
- **Operational basics:** request logging with timings, a `/health` endpoint, graceful shutdown that waits for in-flight requests, request body size limits, and a Dockerfile.
- **Return JSON for every error**, including malformed URIs that the JDK server rejects itself. This probably means a thin servlet/Vert.x/Javalin layer if the API grows.
- **Property-based tests** that compare `PathFinder` against a brute-force search on random graphs.

## AI tools used

I built this with **Claude Code (Claude Opus 5.5)**, Anthropic's CLI coding agent. It read the assignment PDF, proposed the structure, wrote the code, tests and this README, and ran the build and a curl smoke test. During that smoke test it found and fixed a bug: Jackson closed the request stream, so POST responses were never sent. I reviewed the code and can explain and change any part of it.
