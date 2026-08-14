package com.scbck.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.CertificateRequest;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.Student;
import com.scbck.model.StudentCertificate;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentSubject;
import com.scbck.model.User;
import com.scbck.repository.StudentCertificateDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.UserDao;

/**
 * Prepares and issues the two certificates the school hands out.
 *
 * Everything the school already knows is filled in from the record, so the
 * principal is left writing only the parts that are genuinely a judgement -
 * conduct, health observations, activities, the reason for leaving. The old
 * process retyped the lot into a Word file, which is where the mismatched
 * admission numbers came from.
 */
@Service
public class CertificateService {

    private final StudentDao studentDao;
    private final StudentRegistrationDao registrationDao;
    private final StudentSubjectDao studentSubjectDao;
    private final StudentCertificateDao certificateDao;
    private final UserDao userDao;
    private final PrivilegeService privilegeService;

    public CertificateService(StudentDao studentDao, StudentRegistrationDao registrationDao,
            StudentSubjectDao studentSubjectDao, StudentCertificateDao certificateDao,
            UserDao userDao, PrivilegeService privilegeService) {
        this.studentDao = studentDao;
        this.registrationDao = registrationDao;
        this.studentSubjectDao = studentSubjectDao;
        this.certificateDao = certificateDao;
        this.userDao = userDao;
        this.privilegeService = privilegeService;
    }

    /**
     * A draft certificate with every known field already filled in.
     *
     * Not saved: nothing is recorded until the principal has reviewed the
     * wording and issued it.
     */
    @Transactional(readOnly = true)
    public StudentCertificate prefill(Integer studentId, String type) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student " + studentId + " does not exist."));

        String kind = normaliseType(type);

        List<StudentRegistration> history = registrationDao.listByStudent(studentId);
        StudentRegistration first = earliest(history);
        StudentRegistration latest = history.isEmpty() ? null : history.get(0);
        Classroom classroom = latest == null ? null : latest.getClassroom_id();

        StudentCertificate draft = new StudentCertificate();
        draft.setStudent_id(student);
        draft.setType(kind);
        draft.setIssued_date(LocalDate.now());

        draft.setStudentName(student.getFullname());
        draft.setNameWithInitials(student.getCallingname());
        draft.setAdmissionNo(student.getStu_no());
        draft.setReligion(student.getReligion());
        draft.setDate_of_admission(first == null ? null : first.getDate());
        draft.setDate_of_leaving(LocalDate.now());

        if (student.getGuardian_id() != null) {
            draft.setGuardianName(student.getGuardian_id().getFullname());
            draft.setGuardianAddress(student.getGuardian_id().getAddress());
        }
        if (draft.getGuardianAddress() == null) {
            // Falling back to the student's own address is better than a blank
            // line on a form that must not go out incomplete.
            draft.setGuardianAddress(student.getAddress());
        }

        if (classroom != null) {
            draft.setLastGradeCompleted(ReportLayout.gradeName(classroom));
            draft.setMediumOfInstruction(classroom.getMedium());
        }
        draft.setSubjectsStudied(subjectsOf(latest));

        if (StudentCertificate.CHARACTER.equals(kind)) {
            draft.setBody(characterBody(draft, student));
        }

        return draft;
    }

    /**
     * Records an issued certificate.
     *
     * The record is built here field by field rather than by saving whatever
     * the client posted. Only the student id is taken on trust; everything else
     * is text to print, stored as sent so a reprint is the same document
     * however the student's record changes afterwards.
     */
    @Transactional
    public StudentCertificate issue(CertificateRequest request) {
        Student student = studentDao.findById(request.studentId())
                .orElseThrow(() -> ApiException.badRequest("That student does not exist."));

        StudentCertificate record = new StudentCertificate();
        record.setStudent_id(student);
        record.setType(normaliseType(request.type()));
        record.setIssued_date(request.issuedDate() == null ? LocalDate.now() : request.issuedDate());

        record.setStudentName(request.studentName() == null || request.studentName().isBlank()
                ? student.getFullname()
                : request.studentName());
        record.setNameWithInitials(request.nameWithInitials());
        record.setAdmissionNo(request.admissionNo());
        record.setDate_of_admission(request.dateOfAdmission());
        record.setDate_of_leaving(request.dateOfLeaving());
        record.setGuardianName(request.guardianName());
        record.setGuardianAddress(request.guardianAddress());
        record.setReligion(request.religion());
        record.setReasonForLeaving(request.reasonForLeaving());
        record.setLastGradeCompleted(request.lastGradeCompleted());
        record.setMediumOfInstruction(request.mediumOfInstruction());
        record.setSubjectsStudied(request.subjectsStudied());
        record.setConduct(request.conduct());
        record.setHealthNotes(request.healthNotes());
        record.setCoCurricular(request.coCurricular());
        record.setSpecialTalents(request.specialTalents());
        record.setLastExamPassed(request.lastExamPassed());
        record.setBody(request.body());
        record.setPrincipalName(request.principalName());

        record.setAdded_datetime(LocalDateTime.now());
        record.setAdded_user_id(currentUserId());

        return certificateDao.save(record);
    }

    @Transactional(readOnly = true)
    public StudentCertificate require(Integer id) {
        return certificateDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Certificate " + id + " does not exist."));
    }

    // -------------------------------------------------------------------------

    /**
     * The character certificate's opening paragraphs, with the facts filled in.
     *
     * The school's sample is written throughout in male pronouns. Rather than
     * transcribe that, the pronouns follow the student's recorded gender, and a
     * record with no gender gets they/them - which reads correctly and cannot
     * misgender anyone. The principal edits the text before issuing regardless;
     * this is a starting point, not a finished testimonial.
     */
    private String characterBody(StudentCertificate draft, Student student) {
        Pronouns pronouns = Pronouns.forGender(student.getGender());

        String from = draft.getDate_of_admission() == null ? "____________"
                : draft.getDate_of_admission().toString();
        String to = draft.getDate_of_leaving() == null ? "____________"
                : draft.getDate_of_leaving().toString();

        String subjects = draft.getSubjectsStudied() == null || draft.getSubjectsStudied().isBlank()
                ? "____________"
                : draft.getSubjectsStudied();

        return """
                This is to certify that %s has been a student of this institution from %s to %s.

                %s studied in the %s medium, offering %s.

                %s has been a diligent student who showed good academic progress, and has taken a \
                keen interest in the co-curricular and extra-curricular activities of the school.

                %s conduct in school was good, and it is with pleasure that I recommend %s to any \
                institution."""
                .formatted(
                        orBlank(draft.getStudentName()),
                        from,
                        to,
                        pronouns.subjectCap(),
                        orBlank(draft.getMediumOfInstruction()),
                        subjects,
                        orBlank(draft.getStudentName()),
                        pronouns.possessiveCap(),
                        pronouns.object());
    }

    /** The subjects on the student's latest enrolment, comma separated. */
    private String subjectsOf(StudentRegistration registration) {
        if (registration == null) {
            return null;
        }

        List<String> names = studentSubjectDao.listByRegistration(registration.getId()).stream()
                .map(StudentSubject::getClassroom_subject_id)
                .filter(line -> line != null && line.getSubject_detail_id() != null)
                .map(line -> line.getSubject_detail_id().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return names.isEmpty() ? null : String.join(", ", names);
    }

    /**
     * The first enrolment, which is the date of admission.
     *
     * {@code listByStudent} orders newest first, so this cannot just take the
     * head of the list - a student who repeated a year would otherwise be
     * recorded as admitted in the year they repeated.
     */
    private StudentRegistration earliest(List<StudentRegistration> history) {
        return history.stream()
                .filter(registration -> registration.getDate() != null)
                .min(Comparator.comparing(StudentRegistration::getDate))
                .orElse(history.isEmpty() ? null : history.get(history.size() - 1));
    }

    private String normaliseType(String type) {
        String value = type == null ? "" : type.trim().toUpperCase();
        if (!StudentCertificate.LEAVING.equals(value) && !StudentCertificate.CHARACTER.equals(value)) {
            throw ApiException.badRequest(
                    "A certificate is either " + StudentCertificate.LEAVING + " or "
                            + StudentCertificate.CHARACTER + ", not '" + type + "'.");
        }
        return value;
    }

    private Integer currentUserId() {
        User user = userDao.getByUsername(privilegeService.currentUsername());
        return user == null ? null : user.getId();
    }

    private static String orBlank(String value) {
        return value == null || value.isBlank() ? "____________" : value;
    }

    /**
     * The pronouns a testimonial uses for a student.
     *
     * They/them is the default rather than a fallback to "he": a record with no
     * gender recorded is a record that does not say, and guessing would put the
     * wrong word on a signed document.
     */
    private record Pronouns(String subject, String object, String possessive) {

        private static final Pronouns MALE = new Pronouns("he", "him", "his");
        private static final Pronouns FEMALE = new Pronouns("she", "her", "her");
        private static final Pronouns NEUTRAL = new Pronouns("they", "them", "their");

        static Pronouns forGender(String gender) {
            String value = gender == null ? "" : gender.trim().toLowerCase();
            if (value.startsWith("m")) {
                return MALE;
            }
            if (value.startsWith("f")) {
                return FEMALE;
            }
            return NEUTRAL;
        }

        String subjectCap() {
            return capitalise(subject);
        }

        String possessiveCap() {
            return capitalise(possessive);
        }

        private static String capitalise(String word) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
    }

    /** Everything issued to one student. */
    @Transactional(readOnly = true)
    public List<StudentCertificate> listFor(Integer studentId) {
        return certificateDao.listByStudent(studentId);
    }

    /** The issue log. */
    @Transactional(readOnly = true)
    public List<StudentCertificate> listRecent() {
        return certificateDao.listRecent().stream().limit(200).collect(Collectors.toList());
    }
}
