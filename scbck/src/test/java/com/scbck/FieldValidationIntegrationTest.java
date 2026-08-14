package com.scbck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.scbck.model.Employee;
import com.scbck.model.Guardian;
import com.scbck.model.Student;
import com.scbck.model.User;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Proves the record constraints actually fire.
 *
 * The client has carried these rules since the rewrite, and its own header
 * claims "the server enforces the same constraints independently" - which was
 * untrue. Text fields were annotated {@code @NotNull}, and {@code @NotNull}
 * accepts an empty string, so a blank name reached the database whenever a
 * request arrived from anywhere but the form: a stale tab, a script, a retried
 * fetch.
 *
 * Each case below is a value the browser already rejects. The point is that the
 * server now rejects it too.
 */
@SpringBootTest
@ActiveProfiles("test")
class FieldValidationIntegrationTest {

    @Autowired
    private Validator validator;

    // ---- Student -------------------------------------------------------------

    @Test
    @DisplayName("A blank name is refused, not merely a missing one")
    void blankNamesAreRefused() {
        Student student = validStudent();
        student.setFullname("   ");

        assertThat(messagesFor(student, "fullname")).contains("is required");
    }

    @Test
    @DisplayName("A date of birth in the future is refused")
    void futureBirthDateIsRefused() {
        Student student = validStudent();
        student.setDob(LocalDate.now().plusDays(1));

        assertThat(messagesFor(student, "dob")).contains("must be in the past");
    }

    @Test
    @DisplayName("A name of digits is refused")
    void namesMustReadAsNames() {
        Student student = validStudent();
        student.setFullname("12345");

        assertThat(messagesFor(student, "fullname"))
                .anyMatch(message -> message.contains("may only contain letters"));
    }

    @Test
    @DisplayName("An NIC is either 9 digits and a letter, or 12 digits")
    void nicFormatIsEnforced() {
        Student student = validStudent();

        student.setNic("12345");
        assertThat(messagesFor(student, "nic")).isNotEmpty();

        // Both real formats are accepted, and no NIC at all is fine - only
        // older students hold one.
        student.setNic("901234567V");
        assertThat(messagesFor(student, "nic")).isEmpty();

        student.setNic("200512345678");
        assertThat(messagesFor(student, "nic")).isEmpty();

        student.setNic(null);
        assertThat(messagesFor(student, "nic")).isEmpty();
    }

    @Test
    @DisplayName("An over-long address is refused rather than truncated by the database")
    void lengthsAreBounded() {
        Student student = validStudent();
        student.setAddress("x".repeat(300));

        assertThat(messagesFor(student, "address")).contains("is too long");
    }

    @Test
    @DisplayName("A complete student record passes")
    void validStudentPasses() {
        assertThat(validator.validate(validStudent())).isEmpty();
    }

    // ---- Employee ------------------------------------------------------------

    @Test
    @DisplayName("A staff mobile number must be ten local digits")
    void staffPhoneFormatIsEnforced() {
        Employee employee = validEmployee();

        employee.setMobileno("12345");
        assertThat(messagesFor(employee, "mobileno")).contains("must be 10 digits starting with 0");

        employee.setMobileno("0771234567");
        assertThat(messagesFor(employee, "mobileno")).isEmpty();
    }

    @Test
    @DisplayName("A staff email must look like an email")
    void staffEmailIsValidated() {
        Employee employee = validEmployee();
        employee.setEmail("not-an-email");

        assertThat(messagesFor(employee, "email")).isNotEmpty();
    }

    @Test
    @DisplayName("A complete employee record passes")
    void validEmployeePasses() {
        assertThat(validator.validate(validEmployee())).isEmpty();
    }

    // ---- Guardian and User ---------------------------------------------------

    @Test
    @DisplayName("A guardian needs a real contact number")
    void guardianPhoneIsEnforced() {
        Guardian guardian = validGuardian();
        guardian.setMobile("077");

        assertThat(messagesFor(guardian, "mobile")).isNotEmpty();
    }

    @Test
    @DisplayName("A username is at least three characters")
    void usernameHasAMinimum() {
        User user = validUser();
        user.setUsername("ab");

        assertThat(messagesFor(user, "username")).contains("must be 3 to 45 characters");
    }

    @Test
    @DisplayName("An account email must look like an email")
    void accountEmailIsValidated() {
        User user = validUser();
        user.setUseremail("nope");

        assertThat(messagesFor(user, "useremail")).isNotEmpty();
    }

    // -------------------------------------------------------------------------

    private <T> Set<String> messagesFor(T bean, String field) {
        return validator.validate(bean).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(field))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Student validStudent() {
        Student student = new Student();
        student.setFullname("Nadun Wijesekara");
        student.setCallingname("N. Wijesekara");
        student.setBirth_certi_no("BC-3501");
        student.setDob(LocalDate.of(2010, 5, 12));
        student.setGender("Male");
        student.setReligion("Buddhism");
        student.setNationality("Sri Lankan");
        student.setPrevious_scl("None");
        student.setAddress("12 Temple Road, Kandy");
        return student;
    }

    private Employee validEmployee() {
        Employee employee = new Employee();
        employee.setFullname("Rasangika Wickramasinghe");
        employee.setCallingname("Rasangika");
        employee.setNic("851234567V");
        employee.setGender("Female");
        employee.setDob(LocalDate.of(1985, 1, 1));
        employee.setEmail("rasangika@scbc.test");
        employee.setCivilstatus("Single");
        employee.setMobileno("0771234567");
        employee.setAddress("Kandy");
        return employee;
    }

    private Guardian validGuardian() {
        Guardian guardian = new Guardian();
        guardian.setFullname("Sunil Wijesekara");
        guardian.setNic("751234567V");
        guardian.setMobile("0712345678");
        guardian.setAddress("12 Temple Road, Kandy");
        guardian.setRelationship("Father");
        return guardian;
    }

    private User validUser() {
        User user = new User();
        user.setUsername("clerk");
        user.setPassword("hashed");
        user.setUseremail("clerk@scbc.test");
        user.setStatus(true);
        return user;
    }
}
