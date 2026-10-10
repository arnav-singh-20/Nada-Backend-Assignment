package com.nada.people.service;

import com.nada.people.domain.Person;
import com.nada.people.domain.PersonNotFoundException;
import com.nada.people.store.PeopleStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/** Finds the shortest chain of contacts between two people, up to a hop limit. */
public class PathFinder {

    public static final int DEFAULT_MAX_HOPS = 3;

    private final PeopleStore store;
    private final int maxHops;

    public PathFinder(PeopleStore store) {
        this(store, DEFAULT_MAX_HOPS);
    }

    public PathFinder(PeopleStore store, int maxHops) {
        if (maxHops < 0) {
            throw new IllegalArgumentException("maxHops must not be negative");
        }
        this.store = store;
        this.maxHops = maxHops;
    }

    /**
     * Returns the shortest path from {@code fromId} to {@code toId}, both ends included,
     * or empty if the two are more than {@code maxHops} hops apart (or not linked at all).
     *
     * @throws PersonNotFoundException if either person does not exist
     */
    public Optional<List<Person>> shortestPath(long fromId, long toId) {
        Person from = store.get(fromId);
        store.get(toId);

        if (fromId == toId) {
            return Optional.of(List.of(from));
        }

        // Breadth-first search, stopping once the frontier reaches maxHops.
        Map<Long, Long> parentOf = new HashMap<>();
        parentOf.put(fromId, null);
        Queue<Long> frontier = new ArrayDeque<>(List.of(fromId));

        for (int depth = 0; depth < maxHops && !frontier.isEmpty(); depth++) {
            Queue<Long> next = new ArrayDeque<>();
            for (long current : frontier) {
                for (long contact : store.contactIds(current)) {
                    if (parentOf.containsKey(contact)) {
                        continue;
                    }
                    parentOf.put(contact, current);
                    if (contact == toId) {
                        return Optional.of(buildPath(parentOf, toId));
                    }
                    next.add(contact);
                }
            }
            frontier = next;
        }
        return Optional.empty();
    }

    private List<Person> buildPath(Map<Long, Long> parentOf, long toId) {
        List<Person> path = new ArrayList<>();
        for (Long id = toId; id != null; id = parentOf.get(id)) {
            path.add(store.get(id));
        }
        Collections.reverse(path);
        return path;
    }
}
