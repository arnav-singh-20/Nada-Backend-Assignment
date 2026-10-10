package com.nada.people.domain;

/** Thrown when an operation refers to a person ID that does not exist. */
public class PersonNotFoundException extends RuntimeException {

    private final long personId;

    public PersonNotFoundException(long personId) {
        super("no person " + personId);
        this.personId = personId;
    }

    public long personId() {
        return personId;
    }
}
