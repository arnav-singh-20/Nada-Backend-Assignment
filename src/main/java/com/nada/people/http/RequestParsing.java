package com.nada.people.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Helpers that turn raw request input into validated values, or throw a 400. */
final class RequestParsing {

    private RequestParsing() {
    }

    /** Reads the body as a JSON object. Empty bodies, invalid JSON and non-objects are rejected. */
    static JsonNode readJsonObject(ObjectMapper mapper, InputStream body) throws IOException {
        JsonNode node;
        try {
            node = mapper.readTree(body.readAllBytes());
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("invalid JSON");
        }
        if (node == null || !node.isObject()) {
            throw ApiException.badRequest("request body must be a JSON object");
        }
        return node;
    }

    /** Returns a required string field that contains at least one non-whitespace character. */
    static String requireNonBlankString(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw ApiException.badRequest("missing field '" + field + "'");
        }
        if (!value.isTextual()) {
            throw ApiException.badRequest("field '" + field + "' must be a string");
        }
        String text = value.textValue();
        if (text.isBlank()) {
            throw ApiException.badRequest("field '" + field + "' must not be blank");
        }
        return text;
    }

    /** Returns a required field holding a whole number that fits in a long. */
    static long requireId(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw ApiException.badRequest("missing field '" + field + "'");
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw ApiException.badRequest("field '" + field + "' must be an integer ID");
        }
        return value.longValue();
    }

    /** Parses an ID from a path segment or query parameter, or throws a 400. */
    static long parseId(String raw, String name) {
        if (raw == null || raw.isEmpty()) {
            throw ApiException.badRequest("missing parameter '" + name + "'");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("parameter '" + name + "' must be an integer ID, got '" + raw + "'");
        }
    }

    /**
     * Parses a raw query string into a map. When a key is repeated, the first value wins.
     * Keys without '=' map to an empty string.
     */
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("malformed query string");
        }
    }
}
