package com.scbck.dto;

import java.util.List;

/**
 * What aligning a class's timetable to its grade's curriculum would do, or did.
 *
 * Reported rather than performed silently because the operation removes
 * subjects, and removing a subject takes its enrolments and any marks recorded
 * against it. That is the one thing in this system nobody expects to lose as a
 * side effect, so the school is told the cost before paying it: {@code dryRun}
 * asks the question, and only a call that is not a dry run answers it.
 */
public record CurriculumAlignment(
        boolean dryRun,

        /** Classes examined. */
        int classesConsidered,
        /** Classes whose timetable differs from the curriculum. */
        int classesChanged,

        /** Timetable lines that would be, or were, added. */
        int subjectsAdded,
        /** Timetable lines that would be, or were, removed. */
        int subjectsRemoved,

        /**
         * Marks that would be, or were, destroyed by those removals.
         *
         * Non-zero blocks a run that has not been confirmed. A grade 1 class
         * carrying A/L subjects has no marks against them and aligns freely;
         * one where somebody has genuinely been entering marks does not.
         */
        long marksAffected,

        List<ClassChange> changes) {

    /** One class's difference from its curriculum. */
    public record ClassChange(
            Integer classroomId,
            String className,
            String gradeName,
            List<String> added,
            List<String> removed,
            long marksAffected) {
    }
}
