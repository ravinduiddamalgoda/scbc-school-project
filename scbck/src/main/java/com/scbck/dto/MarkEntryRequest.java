package com.scbck.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * A batch of marks for one class and term.
 *
 * Entry is a grid, so it saves as a grid: sending the whole screen in one
 * request means a teacher's work is either recorded or not, rather than half a
 * class landing before a connection drops. It also keeps the calculated sheet
 * from being rebuilt once per cell.
 *
 * @param entries one per cell the teacher touched; untouched cells are absent
 *                from the payload and left exactly as they were
 */
public record MarkEntryRequest(
        @NotNull(message = "is required") Integer classroomId,
        @NotNull(message = "is required") Integer termId,
        @NotEmpty(message = "must contain at least one mark") @Valid List<Entry> entries) {

    /**
     * @param marks  the mark out of 100; null clears a mark that was entered by
     *               mistake, which is not the same as recording a zero
     * @param absent true when the student sat no paper
     */
    public record Entry(
            @NotNull(message = "is required") Integer studentSubjectId,
            @Min(value = 0, message = "cannot be below 0") @Max(value = 100, message = "cannot be above 100") Integer marks,
            Boolean absent,
            String note) {

        public boolean isAbsent() {
            return Boolean.TRUE.equals(absent);
        }

        /** Nothing to record: no mark, not absent, no note. */
        public boolean isBlank() {
            return marks == null && !isAbsent() && (note == null || note.isBlank());
        }
    }
}
