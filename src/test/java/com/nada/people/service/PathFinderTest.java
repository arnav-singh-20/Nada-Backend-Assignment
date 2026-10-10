package com.nada.people.service;

import com.nada.people.domain.Person;
import com.nada.people.domain.PersonNotFoundException;
import com.nada.people.store.PeopleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderTest {

    private PeopleStore store;
    private PathFinder pathFinder;

    @BeforeEach
    void setUp() {
        store = new PeopleStore();
        pathFinder = new PathFinder(store);
    }

    /** Creates people with the given names and links them in a simple chain, in order. */
    private List<Person> chain(String... names) {
        List<Person> people = Arrays.stream(names).map(store::create).toList();
        for (int i = 0; i + 1 < people.size(); i++) {
            store.connect(people.get(i).id(), people.get(i + 1).id());
        }
        return people;
    }

    @Test
    void threeHopChainIsConnected() {
        List<Person> p = chain("A", "B", "C", "D");

        Optional<List<Person>> path = pathFinder.shortestPath(p.get(0).id(), p.get(3).id());

        assertEquals(Optional.of(p), path);
        assertEquals(3, path.orElseThrow().size() - 1);
    }

    @Test
    void fourHopChainIsNotConnected() {
        List<Person> p = chain("A", "B", "C", "D", "E");

        assertTrue(pathFinder.shortestPath(p.get(0).id(), p.get(4).id()).isEmpty());
    }

    @Test
    void pathWorksInBothDirections() {
        List<Person> p = chain("A", "B", "C", "D");

        assertEquals(Optional.of(List.of(p.get(3), p.get(2), p.get(1), p.get(0))),
                pathFinder.shortestPath(p.get(3).id(), p.get(0).id()));
    }

    @Test
    void samePersonIsZeroHops() {
        Person asha = store.create("Asha");

        assertEquals(Optional.of(List.of(asha)), pathFinder.shortestPath(asha.id(), asha.id()));
    }

    @Test
    void directContactIsOneHop() {
        List<Person> p = chain("A", "B");

        assertEquals(Optional.of(p), pathFinder.shortestPath(p.get(0).id(), p.get(1).id()));
    }

    @Test
    void picksShortestPathWhenALongerOneExists() {
        // A - B - C - D - E, plus a shortcut A - X - E
        List<Person> p = chain("A", "B", "C", "D", "E");
        Person x = store.create("X");
        store.connect(p.get(0).id(), x.id());
        store.connect(x.id(), p.get(4).id());

        assertEquals(Optional.of(List.of(p.get(0), x, p.get(4))),
                pathFinder.shortestPath(p.get(0).id(), p.get(4).id()));
    }

    @Test
    void handlesCycles() {
        List<Person> p = chain("A", "B", "C", "D");
        store.connect(p.get(3).id(), p.get(0).id());
        Person outsider = store.create("Outsider");

        assertEquals(2, pathFinder.shortestPath(p.get(0).id(), p.get(2).id()).orElseThrow().size() - 1);
        assertTrue(pathFinder.shortestPath(p.get(0).id(), outsider.id()).isEmpty());
    }

    @Test
    void unconnectedPeopleAreNotConnected() {
        Person a = store.create("A");
        Person b = store.create("B");

        assertTrue(pathFinder.shortestPath(a.id(), b.id()).isEmpty());
    }

    @Test
    void unknownPersonThrows() {
        Person a = store.create("A");

        PersonNotFoundException e = assertThrows(PersonNotFoundException.class,
                () -> pathFinder.shortestPath(a.id(), 99));
        assertEquals(99, e.personId());
        assertThrows(PersonNotFoundException.class, () -> pathFinder.shortestPath(99, a.id()));
        assertThrows(PersonNotFoundException.class, () -> pathFinder.shortestPath(99, 99));
    }

    @Test
    void hopLimitIsConfigurable() {
        List<Person> p = chain("A", "B", "C", "D", "E");

        PathFinder fourHops = new PathFinder(store, 4);

        assertEquals(Optional.of(p), fourHops.shortestPath(p.get(0).id(), p.get(4).id()));
    }
}
