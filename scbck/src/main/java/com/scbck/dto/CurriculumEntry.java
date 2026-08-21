package com.scbck.dto;

/**
 * One subject on a grade's curriculum, flattened for the client.
 *
 * The entity is not sent directly because it nests a whole {@code Grade} and a
 * whole {@code SubjectDetail} inside every row, which for grade 6's thirteen
 * subjects means thirteen copies of the same grade. The timetable editor, the
 * marks screen and both subject reports all want the same four facts.
 */
public record CurriculumEntry(
        Integer id,
        Integer gradeId,
        String gradeName,
        Integer subjectId,
        String subjectName,
        String subjectCode,
        /** "Core", "Cat 1", "Cat 2", "Cat 3" or "General". */
        String basket,
        Integer sortOrder,
        /**
         * True when the class teacher takes the subject, so the Subject Wise
         * Teachers report expects one teacher per class rather than a count.
         */
        boolean classTeacherTaught) {
}
