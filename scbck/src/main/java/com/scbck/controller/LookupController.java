package com.scbck.controller;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.model.AcademicYear;
import com.scbck.model.Designation;
import com.scbck.model.Employee;
import com.scbck.model.Grade;
import com.scbck.model.Module;
import com.scbck.model.PaymentType;
import com.scbck.model.RegistrationStatus;
import com.scbck.model.Role;
import com.scbck.model.Status;
import com.scbck.model.StudentStatus;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.DesignationDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.ModuleDao;
import com.scbck.repository.PaymentTypeDao;
import com.scbck.repository.RegistrationStatusDao;
import com.scbck.repository.RoleDao;
import com.scbck.repository.StatusDao;
import com.scbck.repository.StudentStatusDao;
import com.scbck.repository.SubjectDetailDao;

/**
 * Read-only reference data used to populate dropdowns.
 *
 * These six lists previously lived in six separate controllers that differed
 * only in the entity they returned. Any authenticated user may read them -
 * they contain no record-level data, and the forms are useless without them.
 */
@RestController
@RequestMapping("/api/lookups")
public class LookupController {

    private final DesignationDao designationDao;
    private final GradeDao gradeDao;
    private final StatusDao statusDao;
    private final StudentStatusDao studentStatusDao;
    private final ModuleDao moduleDao;
    private final RoleDao roleDao;
    private final AcademicYearDao academicYearDao;
    private final RegistrationStatusDao registrationStatusDao;
    private final SubjectDetailDao subjectDao;
    private final PaymentTypeDao paymentTypeDao;

    public LookupController(DesignationDao designationDao, GradeDao gradeDao, StatusDao statusDao,
            StudentStatusDao studentStatusDao, ModuleDao moduleDao, RoleDao roleDao,
            AcademicYearDao academicYearDao, RegistrationStatusDao registrationStatusDao,
            SubjectDetailDao subjectDao, PaymentTypeDao paymentTypeDao) {
        this.designationDao = designationDao;
        this.gradeDao = gradeDao;
        this.statusDao = statusDao;
        this.studentStatusDao = studentStatusDao;
        this.moduleDao = moduleDao;
        this.roleDao = roleDao;
        this.academicYearDao = academicYearDao;
        this.registrationStatusDao = registrationStatusDao;
        this.subjectDao = subjectDao;
        this.paymentTypeDao = paymentTypeDao;
    }

    @GetMapping("/designations")
    public List<Designation> designations() {
        return designationDao.findAll(Sort.by("name"));
    }

    @GetMapping("/grades")
    public List<Grade> grades() {
        return gradeDao.findAll(Sort.by("id"));
    }

    @GetMapping("/statuses")
    public List<Status> statuses() {
        return statusDao.findAll(Sort.by("id"));
    }

    @GetMapping("/student-statuses")
    public List<StudentStatus> studentStatuses() {
        return studentStatusDao.findAll(Sort.by("id"));
    }

    @GetMapping("/modules")
    public List<Module> modules() {
        return moduleDao.findAll(Sort.by("name"));
    }

    @GetMapping("/roles")
    public List<Role> roles() {
        return roleDao.findAll(Sort.by("name"));
    }

    /** Roles that can be assigned through the UI; Admin is reserved. */
    @GetMapping("/roles/assignable")
    public List<Role> assignableRoles() {
        return roleDao.listWithoutAdmin();
    }

    /** Newest first: the year a form should default to is the one at the top. */
    @GetMapping("/academic-years")
    public List<AcademicYear> academicYears() {
        return academicYearDao.findAll(Sort.by(Sort.Direction.DESC, "name"));
    }

    @GetMapping("/registration-statuses")
    public List<RegistrationStatus> registrationStatuses() {
        return registrationStatusDao.findAll(Sort.by("id"));
    }

    /** Only subjects still in use; retired ones stay out of the pickers. */
    @GetMapping("/subjects")
    public List<SubjectDetail> subjects() {
        return subjectDao.listActive();
    }

    @GetMapping("/payment-types")
    public List<PaymentType> paymentTypes() {
        return paymentTypeDao.findAll(Sort.by("id"));
    }

    /**
     * The two media of instruction. A fixed list rather than a table: adding a
     * third would mean a new column in the Medium wise report, which is a code
     * change either way, and a lookup table would only hide that.
     */
    @GetMapping("/mediums")
    public List<String> mediums() {
        return List.of("Sinhala", "English");
    }

    /**
     * How a teacher is engaged, and the qualification ladder.
     *
     * Served from the same constants the entity documents so the form can only
     * ever offer what the record is meant to hold.
     */
    @GetMapping("/appointment-types")
    public List<String> appointmentTypes() {
        return Employee.APPOINTMENT_TYPES;
    }

    @GetMapping("/education-qualifications")
    public List<String> educationQualifications() {
        return Employee.EDUCATION_QUALIFICATIONS;
    }
}
