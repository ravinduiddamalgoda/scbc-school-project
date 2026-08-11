package com.scbck.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.repository.AcademicYearDao;

/**
 * Decides which academic year a request is about.
 *
 * Class listings and every report take an optional year; leaving it off has to
 * mean something sensible rather than "all years at once", because a report
 * that mixed 2025 and 2026 enrolments would double every class size. The rule
 * is: the year asked for, else the one flagged current, else the newest.
 */
@Service
public class AcademicYearService {

    private final AcademicYearDao academicYearDao;

    public AcademicYearService(AcademicYearDao academicYearDao) {
        this.academicYearDao = academicYearDao;
    }

    /** @param requestedId may be null */
    public AcademicYear resolve(Integer requestedId) {
        if (requestedId != null) {
            return academicYearDao.findById(requestedId)
                    .orElseThrow(() -> ApiException.notFound("Academic year " + requestedId + " does not exist."));
        }

        List<AcademicYear> current = academicYearDao.listCurrent();
        if (!current.isEmpty()) {
            return current.get(0);
        }

        List<AcademicYear> all = academicYearDao.findAll(Sort.by(Sort.Direction.DESC, "name"));
        if (all.isEmpty()) {
            throw ApiException.badRequest(
                    "No academic year has been set up yet. Create one before adding classes or running reports.");
        }
        return all.get(0);
    }
}
