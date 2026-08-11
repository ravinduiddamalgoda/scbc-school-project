package com.scbck.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Self-service profile edit for the signed-in user.
 *
 * The target account is always taken from the session, never from the payload,
 * so one user can no longer submit another user's name and edit their record.
 */
public record ChangeProfileRequest(
        @NotBlank(message = "is required") String username,
        @Email(message = "must be a valid email address") @NotBlank(message = "is required") String email,
        String photo,
        String oldPassword,
        String newPassword) {
}
