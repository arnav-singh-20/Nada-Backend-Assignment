package com.nada.people.domain;

/** A person known to the network. Immutable; the name is stored exactly as supplied. */
public record Person(long id, String name) {
}
