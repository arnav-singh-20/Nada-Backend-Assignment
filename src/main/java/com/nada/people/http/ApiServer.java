package com.nada.people.http;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nada.people.service.PathFinder;
import com.nada.people.store.PeopleStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wires the store, path finder and HTTP handlers into a running JDK HTTP server. */
public class ApiServer {

    private final HttpServer server;
    private final ExecutorService executor;

    private ApiServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    /** Starts a server on the given port with an empty store. Use port 0 for any free port. */
    public static ApiServer start(int port) throws IOException {
        PeopleStore store = new PeopleStore();
        PeopleApi api = new PeopleApi(store, new PathFinder(store), objectMapper());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
        server.setExecutor(executor);
        server.createContext("/", api.router());
        server.start();
        return new ApiServer(server, executor);
    }

    static ObjectMapper objectMapper() {
        return JsonMapper.builder()
                // Reject bodies such as {"name": "Asha"} garbage, and {"name": "A", "name": "B"}.
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }
}
