package com.scbck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a class.
 *
 * @param classTeacherId optional - a class may exist before a teacher is
 *                       assigned to it, and the Class Teachers report prints
 *                       "Not assigned" for those rather than hiding them
 * @param medium         "Sinhala" or "English"; optional, and the Medium wise
 *                       Student Count report counts anything unset in its own
 *                       column rather than guessing
 */
public record ClassroomRequest(
        @NotBlank(message = "is required") @Size(max = 45, message = "must be at most 45 characters") String name,
        @NotNull(message = "is required") Integer gradeId,
        @NotNull(message = "is required") Integer academicYearId,
        Integer classTeacherId,
        String medium) {
}
