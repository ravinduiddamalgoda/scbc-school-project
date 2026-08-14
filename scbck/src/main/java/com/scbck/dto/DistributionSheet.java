package com.scbck.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One class's uniform or book distribution.
 *
 * The same shape as the paper sheet it replaces: a roster down the side, an
 * item per column, and a signature column left blank for the student to sign
 * on collection.
 */
public record DistributionSheet(
        Integer classroomId,
        String className,
        String gradeName,
        String academicYear,
        /** "UNIFORM" or "BOOK". */
        String kind,
        String title,
        LocalDateTime generatedAt,
        List<Item> items,
        List<Row> rows) {

    public record Item(Integer id, String name, String code) {
    }

    /**
     * @param cells    quantities positionally aligned to {@link #items()}
     * @param issued   how many of this student's items have been issued, for
     *                 the screen's progress badge
     */
    public record Row(
            int index,
            Integer registrationId,
            Integer studentId,
            String admissionNo,
            String studentName,
            List<Cell> cells,
            int issued) {
    }

    public record Cell(Integer itemId, Integer quantity, String note) {
    }
}
