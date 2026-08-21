package com.scbck.dto;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create/update payload for the User module.
 *
 * Password is explicit and optional: on create it is required and hashed, on
 * update a blank value leaves the stored hash untouched. The entity used to be
 * bound directly, so an update round-tripped whatever the client sent into the
 * password column - storing it verbatim when the caller was not the browser.
 */
public record UserRequest(
        Integer id,

        @NotBlank(message = "is required") String username,

        @Email(message = "must be a valid email address") @NotBlank(message = "is required") String useremail,

        /** Required on create; leave blank on update to keep the current password. */
        String password,

        @NotNull(message = "is required") Boolean status,

        String note,

        String photo,

        /** Optional link to the staff record this login belongs to. */
        Integer employeeId,

        /**
         * Optional link to the guardian record this login belongs to.
         *
         * What makes an account a parent account: it is the link the portal
         * derives the child list from, so an account with the Parent role and
         * no guardian can sign in but sees nothing, and says so.
         */
        Integer guardianId,

        @NotNull(message = "is required") Set<Integer> roleIds) {
}
