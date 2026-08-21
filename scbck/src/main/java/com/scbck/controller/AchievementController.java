package com.scbck.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.Student;
import com.scbck.model.StudentAchievement;
import com.scbck.model.User;
import com.scbck.repository.StudentAchievementDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.UserDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * The record behind the last four items on the leaving certificate.
 *
 * Gated on the Achievement module rather than on Student, so the school can
 * give a sports master or a prefect-of-games the right to record a
 * championship without also handing over the ability to edit admission numbers
 * and guardian addresses.
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final StudentAchievementDao achievementDao;
    private final StudentDao studentDao;
    private final UserDao userDao;
    private final PrivilegeService privilegeService;

    public AchievementController(StudentAchievementDao achievementDao, StudentDao studentDao,
            UserDao userDao, PrivilegeService privilegeService) {
        this.achievementDao = achievementDao;
        this.studentDao = studentDao;
        this.userDao = userDao;
        this.privilegeService = privilegeService;
    }

    /** Everything recorded about one student, or one kind of it. */
    @GetMapping
    public List<StudentAchievement> list(@RequestParam Integer studentId,
            @RequestParam(required = false) String kind) {

        privilegeService.requireSelect(PrivilegeService.MODULE_ACHIEVEMENT);

        return kind == null || kind.isBlank()
                ? achievementDao.listForStudent(studentId)
                : achievementDao.listForStudentAndKind(studentId, kind.trim().toUpperCase());
    }

    /**
     * The dropdown contents, so the form is never out of step with what the
     * server will accept.
     *
     * These used to be hard-coded arrays in the browser, which is how the
     * subject categories ended up unchangeable without a release. Serving them
     * from the same constants the validation reads means a list can only ever
     * be wrong in one place.
     */
    @GetMapping("/options")
    public Map<String, List<String>> options() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("kinds", StudentAchievement.KINDS);
        options.put("leadershipTypes", StudentAchievement.LEADERSHIP_TYPES);
        options.put("coCurricularTypes", StudentAchievement.CO_CURRICULAR_TYPES);
        options.put("sports", StudentAchievement.SPORTS);
        return options;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<StudentAchievement> create(
            @Valid @RequestBody StudentAchievement achievement) {

        privilegeService.requireInsert(PrivilegeService.MODULE_ACHIEVEMENT);

        Student student = requireStudent(achievement);
        assertKind(achievement.getKind());

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        achievement.setId(null);
        achievement.setStudent_id(student);
        achievement.setKind(achievement.getKind().trim().toUpperCase());
        achievement.setAdded_datetime(LocalDateTime.now());
        achievement.setAdded_user_id(currentUser == null ? null : currentUser.getId());
        achievement.setUpdated_datetime(null);
        achievement.setUpdated_user_id(null);
        normalise(achievement);

        return ResponseEntity.status(HttpStatus.CREATED).body(achievementDao.save(achievement));
    }

    @PutMapping("/{id}")
    @Transactional
    public StudentAchievement update(@PathVariable Integer id,
            @Valid @RequestBody StudentAchievement achievement) {

        privilegeService.requireUpdate(PrivilegeService.MODULE_ACHIEVEMENT);

        StudentAchievement existing = require(id);
        assertKind(achievement.getKind());

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());

        // The student is never moved: an achievement belongs to the student it
        // was recorded against, and reassigning it would be a way to rewrite
        // two records at once with no trace of either.
        existing.setKind(achievement.getKind().trim().toUpperCase());
        existing.setType(achievement.getType());
        existing.setSubType(achievement.getSubType());
        existing.setOtherType(achievement.getOtherType());
        existing.setDetail(achievement.getDetail());
        existing.setYear(achievement.getYear());
        existing.setUpdated_datetime(LocalDateTime.now());
        existing.setUpdated_user_id(currentUser == null ? null : currentUser.getId());
        normalise(existing);

        return achievementDao.save(existing);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_ACHIEVEMENT);

        StudentAchievement existing = require(id);
        achievementDao.delete(existing);
        return MessageResponse.of("Removed.");
    }

    // -------------------------------------------------------------------------

    private StudentAchievement require(Integer id) {
        return achievementDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Record " + id + " does not exist."));
    }

    private Student requireStudent(StudentAchievement achievement) {
        if (achievement.getStudent_id() == null || achievement.getStudent_id().getId() == null) {
            throw ApiException.badRequest("A student is required.");
        }
        Integer studentId = achievement.getStudent_id().getId();
        return studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student " + studentId + " does not exist."));
    }

    private void assertKind(String kind) {
        if (kind == null || !StudentAchievement.KINDS.contains(kind.trim().toUpperCase())) {
            throw ApiException.badRequest(
                    "Kind must be one of " + StudentAchievement.KINDS + ".");
        }
    }

    /**
     * Clears the fields the chosen type cannot have.
     *
     * A sub-type left behind after the type is changed from "Sport" to "Music"
     * would print "Music (Cricket)" on a certificate, and the only place that
     * mistake would show up is the document itself.
     */
    private void normalise(StudentAchievement achievement) {
        boolean isSport = "Sport".equalsIgnoreCase(achievement.getType());
        if (!isSport) {
            achievement.setSubType(null);
        }
        if (!"Other".equalsIgnoreCase(achievement.getType())) {
            achievement.setOtherType(null);
        }
        if (StudentAchievement.CONDUCT.equals(achievement.getKind())
                || StudentAchievement.HEALTH.equals(achievement.getKind())) {
            // Conduct and health are a single observation with no type to pick.
            achievement.setType(null);
            achievement.setSubType(null);
            achievement.setOtherType(null);
        }
    }
}
