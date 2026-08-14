package com.scbck.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.MarkEntryRequest;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.StudentMark;
import com.scbck.model.StudentSubject;
import com.scbck.model.Term;
import com.scbck.model.User;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.StudentMarkDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.UserDao;

/**
 * Writes a batch of marks.
 *
 * Every entry is checked against the class it claims to belong to before
 * anything is written. The enrolment id alone would be enough to find the row,
 * but not to prove it belongs to the class the teacher had open - and a stale
 * browser tab, or an id typed into the API by hand, would otherwise write a
 * mark into another class's sheet with no error anywhere.
 */
@Service
public class MarkEntryService {

    private final StudentMarkDao markDao;
    private final StudentSubjectDao studentSubjectDao;
    private final ClassroomDao classroomDao;
    private final UserDao userDao;
    private final MarkSheetService markSheetService;
    private final PrivilegeService privilegeService;

    public MarkEntryService(StudentMarkDao markDao, StudentSubjectDao studentSubjectDao,
            ClassroomDao classroomDao, UserDao userDao, MarkSheetService markSheetService,
            PrivilegeService privilegeService) {
        this.markDao = markDao;
        this.studentSubjectDao = studentSubjectDao;
        this.classroomDao = classroomDao;
        this.userDao = userDao;
        this.markSheetService = markSheetService;
        this.privilegeService = privilegeService;
    }

    /**
     * @return how many marks were written, updated or cleared
     */
    @Transactional
    public int save(MarkEntryRequest request) {

        Classroom classroom = classroomDao.findById(request.classroomId())
                .orElseThrow(() -> ApiException.notFound("Class " + request.classroomId() + " does not exist."));
        Term term = markSheetService.requireTermOf(classroom, request.termId());

        Integer userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();

        List<StudentMark> toSave = new ArrayList<>();
        List<StudentMark> toDelete = new ArrayList<>();

        for (MarkEntryRequest.Entry entry : request.entries()) {
            StudentSubject enrolment = studentSubjectDao.findById(entry.studentSubjectId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Enrolment " + entry.studentSubjectId() + " does not exist."));

            assertBelongsTo(enrolment, classroom);

            if (entry.marks() != null && entry.isAbsent()) {
                throw ApiException.badRequest(studentName(enrolment)
                        + " cannot be both absent and have a mark in " + subjectName(enrolment) + ".");
            }

            StudentMark existing = markDao.getByStudentSubjectAndTerm(enrolment.getId(), term.getId());

            // A cleared cell removes the row rather than storing an empty one, so
            // "no mark entered yet" stays a single state in the database.
            if (entry.isBlank()) {
                if (existing != null) {
                    toDelete.add(existing);
                }
                continue;
            }

            StudentMark mark = existing == null ? newMark(enrolment, term, now) : existing;
            mark.setMarks(entry.marks());
            mark.setAbsent(entry.isAbsent());
            mark.setNote(entry.note() == null || entry.note().isBlank() ? null : entry.note().trim());
            mark.setUpdated_datetime(now);
            mark.setUpdated_user_id(userId);

            toSave.add(mark);
        }

        markDao.saveAll(toSave);
        markDao.deleteAll(toDelete);

        return toSave.size() + toDelete.size();
    }

    // -------------------------------------------------------------------------

    private StudentMark newMark(StudentSubject enrolment, Term term, LocalDateTime now) {
        StudentMark mark = new StudentMark();
        mark.setStudent_subject_id(enrolment);
        mark.setTerm_id(term);
        mark.setAdded_datetime(now);
        return mark;
    }

    private void assertBelongsTo(StudentSubject enrolment, Classroom classroom) {
        Integer enrolmentClass = enrolment.getClassroom_subject_id() == null
                || enrolment.getClassroom_subject_id().getClassroom_id() == null
                        ? null
                        : enrolment.getClassroom_subject_id().getClassroom_id().getId();

        if (!Objects.equals(enrolmentClass, classroom.getId())) {
            throw ApiException.badRequest(studentName(enrolment) + "'s enrolment in "
                    + subjectName(enrolment) + " is not on " + ReportLayout.classLabel(classroom)
                    + "'s timetable. Reload the sheet and try again.");
        }
    }

    /**
     * The id behind the current username, for the audit column.
     *
     * A missing user row is not fatal: the mark is worth more than the trail,
     * so it is recorded with a null author rather than refused.
     */
    private Integer currentUserId() {
        User user = userDao.getByUsername(privilegeService.currentUsername());
        return user == null ? null : user.getId();
    }

    private String studentName(StudentSubject enrolment) {
        if (enrolment.getStudent_registration_id() == null
                || enrolment.getStudent_registration_id().getStudent_id() == null) {
            return "This student";
        }
        return enrolment.getStudent_registration_id().getStudent_id().getFullname();
    }

    private String subjectName(StudentSubject enrolment) {
        if (enrolment.getClassroom_subject_id() == null
                || enrolment.getClassroom_subject_id().getSubject_detail_id() == null) {
            return "this subject";
        }
        return enrolment.getClassroom_subject_id().getSubject_detail_id().getName();
    }
}
