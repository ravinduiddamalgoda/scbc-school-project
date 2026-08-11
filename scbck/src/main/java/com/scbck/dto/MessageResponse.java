package com.scbck.dto;

/**
 * Simple acknowledgement body for mutating endpoints that have nothing else
 * to return. Replaces the old plain-text "OK" / "Save not completed: ..."
 * protocol, which forced the client to string-match on error text.
 */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
