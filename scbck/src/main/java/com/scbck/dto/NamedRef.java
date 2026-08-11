package com.scbck.dto;

/**
 * The id and label of a related record, which is all a list or a dropdown ever
 * needs from one. Returning this instead of the entity keeps a class listing
 * from dragging an employee's photo, NIC and address along with it.
 */
public record NamedRef(Integer id, String name) {

    public static NamedRef of(Integer id, String name) {
        return id == null ? null : new NamedRef(id, name);
    }
}
