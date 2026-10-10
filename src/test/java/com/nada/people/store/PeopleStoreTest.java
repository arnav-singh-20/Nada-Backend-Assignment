package com.nada.people.store;

import com.nada.people.domain.Person;
import com.nada.people.domain.PersonNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeopleStoreTest {

    private final PeopleStore store = new PeopleStore();

    @Test
    void assignsSequentialIds() {
        assertEquals(new Person(1, "Asha"), store.create("Asha"));
        assertEquals(new Person(2, "Rohan"), store.create("Rohan"));
    }

    @Test
    void connectionsAreMutual() {
        Person asha = store.create("Asha");
        Person rohan = store.create("Rohan");

        store.connect(asha.id(), rohan.id());

        assertEquals(List.of(rohan), store.contactsOf(asha.id()));
        assertEquals(List.of(asha), store.contactsOf(rohan.id()));
    }

    @Test
    void connectingTwiceIsIdempotent() {
        Person asha = store.create("Asha");
        Person rohan = store.create("Rohan");

        store.connect(asha.id(), rohan.id());
        store.connect(rohan.id(), asha.id());

        assertEquals(List.of(rohan), store.contactsOf(asha.id()));
    }

    @Test
    void contactsAreSortedById() {
        Person hub = store.create("Hub");
        Person c = store.create("C");
        Person b = store.create("B");
        store.connect(hub.id(), b.id());
        store.connect(hub.id(), c.id());

        assertEquals(List.of(c, b), store.contactsOf(hub.id()));
    }

    @Test
    void newPersonHasNoContacts() {
        assertTrue(store.contactsOf(store.create("Asha").id()).isEmpty());
    }

    @Test
    void unknownPeopleAreRejected() {
        Person asha = store.create("Asha");

        assertThrows(PersonNotFoundException.class, () -> store.get(42));
        assertThrows(PersonNotFoundException.class, () -> store.contactsOf(42));
        assertThrows(PersonNotFoundException.class, () -> store.connect(asha.id(), 42));
        assertTrue(store.contactsOf(asha.id()).isEmpty(), "failed connect must not leave a half edge");
    }
}
