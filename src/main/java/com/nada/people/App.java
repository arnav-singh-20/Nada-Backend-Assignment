package com.nada.people;

import com.nada.people.http.ApiServer;

import java.io.IOException;

/** Entry point. Listens on the port in the PORT environment variable, or 8080 by default. */
public final class App {

    private static final int DEFAULT_PORT = 8080;

    private App() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(System.getenv("PORT"));
        ApiServer server = ApiServer.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("People API listening on http://localhost:" + server.port());
    }

    private static int resolvePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a number, got '" + value + "'", e);
        }
    }
}
