package com.nada.people.store;

import com.nada.people.domain.Person;
import com.nada.people.domain.PersonNotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory store of people and their mutual "knows" connections.
 *
 * <p>The graph is undirected: connecting a to b also connects b to a. Contacts are kept
 * sorted by ID so responses and path choices are deterministic. All methods are
 * synchronized, which is plenty for this workload and keeps the person map and the
 * adjacency map consistent with each other.
 */
public class PeopleStore {

    private final Map<Long, Person> people = new HashMap<>();
    private final Map<Long, Set<Long>> contacts = new HashMap<>();
    private long nextId = 1;

    public synchronized Person create(String name) {
        Person person = new Person(nextId++, name);
        people.put(person.id(), person);
        contacts.put(person.id(), new TreeSet<>());
        return person;
    }

    /** Returns the person with this ID, or throws {@link PersonNotFoundException}. */
    public synchronized Person get(long id) {
        Person person = people.get(id);
        if (person == null) {
            throw new PersonNotFoundException(id);
        }
        return person;
    }

    /** Connects two existing people. Connecting an already-connected pair is a no-op. */
    public synchronized void connect(long a, long b) {
        get(a);
        get(b);
        contacts.get(a).add(b);
        contacts.get(b).add(a);
    }

    /** Returns the IDs of the direct contacts of an existing person, in ascending order. */
    public synchronized List<Long> contactIds(long id) {
        get(id);
        return new ArrayList<>(contacts.get(id));
    }

    /** Returns the direct contacts of an existing person, in ascending ID order. */
    public synchronized List<Person> contactsOf(long id) {
        return contactIds(id).stream().map(people::get).toList();
    }
}
