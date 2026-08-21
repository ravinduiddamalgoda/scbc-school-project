package com.scbck.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.DistributionSheet;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.DistributionItem;
import com.scbck.model.StudentDistribution;
import com.scbck.model.StudentRegistration;
import com.scbck.model.User;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.DistributionDao;
import com.scbck.repository.DistributionItemDao;
import com.scbck.repository.UserDao;

/**
 * Builds and saves the uniform and book distribution sheets.
 *
 * Deliberately the same shape as {@link MarkSheetService}: a class, a set of
 * columns, a roster, and a grid of numbers. The two screens then behave the
 * same way, which matters more than it sounds - the same clerk uses both, a
 * fortnight apart.
 */
@Service
public class DistributionService {

    private final ClassroomDao classroomDao;
    private final DistributionDao distributionDao;
    private final DistributionItemDao itemDao;
    private final UserDao userDao;
    private final MarkSheetService markSheetService;
    private final PrivilegeService privilegeService;

    public DistributionService(ClassroomDao classroomDao, DistributionDao distributionDao,
            DistributionItemDao itemDao, UserDao userDao, MarkSheetService markSheetService,
            PrivilegeService privilegeService) {
        this.classroomDao = classroomDao;
        this.distributionDao = distributionDao;
        this.itemDao = itemDao;
        this.userDao = userDao;
        this.markSheetService = markSheetService;
        this.privilegeService = privilegeService;
    }

    @Transactional(readOnly = true)
    public DistributionSheet build(Integer classroomId, String kind) {
        Classroom classroom = classroomDao.findById(classroomId)
                .orElseThrow(() -> ApiException.notFound("Class " + classroomId + " does not exist."));

        String type = normaliseKind(kind);
        Integer gradeId = classroom.getGrade_id() == null ? null : classroom.getGrade_id().getId();

        List<DistributionItem> items = itemDao.listForSheet(type, gradeId);
        if (items.isEmpty()) {
            // Names both places the list can be edited from. The message used
            // to send people to Academic setup, which had no such screen at the
            // time - so the instruction was correct about the intent and wrong
            // about where to act on it.
            throw ApiException.badRequest("No " + type.toLowerCase()
                    + " items have been set up for " + ReportLayout.gradeName(classroom)
                    + " yet. Add them with the \"Manage items\" button on this screen, or under"
                    + " Academic setup.");
        }

        // Same roster and the same activity filter as the mark sheet, so the two
        // can never disagree about who is in the class.
        List<StudentRegistration> roster = markSheetService.activeRoster(classroomId);

        Map<String, StudentDistribution> issued = new HashMap<>();
        for (StudentDistribution record : distributionDao.listByClassroomAndKind(classroomId, type)) {
            issued.put(key(record.getStudent_registration_id().getId(),
                    record.getDistribution_item_id().getId()), record);
        }

        List<DistributionSheet.Row> rows = new ArrayList<>();
        int index = 1;
        for (StudentRegistration registration : roster) {
            List<DistributionSheet.Cell> cells = new ArrayList<>();
            int count = 0;

            for (DistributionItem item : items) {
                StudentDistribution record = issued.get(key(registration.getId(), item.getId()));
                Integer quantity = record == null ? null : record.getQuantity();
                if (quantity != null && quantity > 0) {
                    count++;
                }
                cells.add(new DistributionSheet.Cell(item.getId(), quantity,
                        record == null ? null : record.getNote()));
            }

            rows.add(new DistributionSheet.Row(
                    index++,
                    registration.getId(),
                    registration.getStudent_id() == null ? null : registration.getStudent_id().getId(),
                    registration.getStudent_id() == null ? null : registration.getStudent_id().getStu_no(),
                    registration.getStudent_id() == null ? "—" : registration.getStudent_id().getFullname(),
                    cells,
                    count));
        }

        return new DistributionSheet(
                classroom.getId(),
                ReportLayout.classLabel(classroom),
                ReportLayout.gradeName(classroom),
                classroom.getAcademic_year_id() == null ? null : classroom.getAcademic_year_id().getName(),
                type,
                DistributionItem.BOOK.equals(type) ? "Distribution of Books" : "Distribution of Uniforms",
                LocalDateTime.now(),
                items.stream()
                        .map(item -> new DistributionSheet.Item(item.getId(), item.getName(),
                                item.getCode() == null || item.getCode().isBlank()
                                        ? item.getName()
                                        : item.getCode()))
                        .toList(),
                rows);
    }

    /**
     * Records a screen's worth of issues.
     *
     * @return how many rows were written or cleared
     */
    @Transactional
    public int save(Integer classroomId, String kind, List<Entry> entries) {
        Classroom classroom = classroomDao.findById(classroomId)
                .orElseThrow(() -> ApiException.notFound("Class " + classroomId + " does not exist."));
        String type = normaliseKind(kind);

        Integer userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();

        // The roll is read once and indexed. Looking each entry up against a
        // fresh query would be one round trip per cell of a grid that is saved
        // a whole class at a time.
        Map<Integer, StudentRegistration> roll = new HashMap<>();
        for (StudentRegistration registration : markSheetService.activeRoster(classroomId)) {
            roll.put(registration.getId(), registration);
        }

        List<StudentDistribution> toSave = new ArrayList<>();
        List<StudentDistribution> toDelete = new ArrayList<>();

        for (Entry entry : entries) {
            StudentRegistration registration = roll.get(entry.registrationId());
            if (registration == null) {
                throw ApiException.badRequest(
                        "That student is not on " + ReportLayout.classLabel(classroom) + "'s roll.");
            }

            DistributionItem item = itemDao.findById(entry.itemId())
                    .orElseThrow(() -> ApiException.badRequest("Item " + entry.itemId() + " does not exist."));

            if (!type.equals(item.getKind())) {
                throw ApiException.badRequest(item.getName() + " is not a " + type.toLowerCase() + " item.");
            }

            StudentDistribution existing =
                    distributionDao.getByRegistrationAndItem(registration.getId(), item.getId());

            // Nothing issued is the absence of a row, not a stored zero - the
            // sheet has to distinguish "not yet given out" from "given none".
            if (entry.quantity() == null || entry.quantity() <= 0) {
                if (existing != null) {
                    toDelete.add(existing);
                }
                continue;
            }

            StudentDistribution record = existing == null ? new StudentDistribution() : existing;
            record.setStudent_registration_id(registration);
            record.setDistribution_item_id(item);
            record.setQuantity(entry.quantity());
            record.setNote(entry.note() == null || entry.note().isBlank() ? null : entry.note().trim());
            if (record.getIssued_date() == null) {
                record.setIssued_date(LocalDate.now());
            }
            record.setUpdated_datetime(now);
            record.setUpdated_user_id(userId);

            toSave.add(record);
        }

        distributionDao.saveAll(toSave);
        distributionDao.deleteAll(toDelete);
        return toSave.size() + toDelete.size();
    }

    // -------------------------------------------------------------------------

    private String normaliseKind(String kind) {
        String value = kind == null ? "" : kind.trim().toUpperCase();
        if (!DistributionItem.UNIFORM.equals(value) && !DistributionItem.BOOK.equals(value)) {
            throw ApiException.badRequest("A distribution is either UNIFORM or BOOK, not '" + kind + "'.");
        }
        return value;
    }

    private String key(Integer registrationId, Integer itemId) {
        return registrationId + ":" + itemId;
    }

    private Integer currentUserId() {
        User user = userDao.getByUsername(privilegeService.currentUsername());
        return user == null ? null : user.getId();
    }

    /** One cell of the grid. */
    public record Entry(Integer registrationId, Integer itemId, Integer quantity, String note) {
    }
}
