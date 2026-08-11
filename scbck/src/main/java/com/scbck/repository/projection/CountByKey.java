package com.scbck.repository.projection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A grouped count keyed by the id of whatever was grouped on.
 *
 * The report queries aggregate in the database rather than loading the roll
 * into memory: a full school is tens of thousands of student_subject rows, and
 * the reports only ever need the totals.
 */
public interface CountByKey {

    Integer getKeyId();

    long getTotal();

    /** Turns a grouped result into the lookup every caller actually wants. */
    static Map<Integer, Long> toMap(List<? extends CountByKey> rows) {
        Map<Integer, Long> counts = new HashMap<>();
        for (CountByKey row : rows) {
            counts.put(row.getKeyId(), row.getTotal());
        }
        return counts;
    }
}
