package com.scbck.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.SubjectCategory;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.SubjectCategoryDao;
import com.scbck.repository.SubjectDetailDao;

/**
 * Turns the free-text {@code subject_detail.category} values that existing
 * databases already hold into {@link SubjectCategory} rows, and points each
 * subject at the row matching the text it used to carry.
 *
 * Without this, upgrading loses the grouping silently: Hibernate adds the new
 * {@code subject_category_id} column as null, every subject reports "no
 * category", and the mark sheet's bands collapse into one. The seed script's
 * "Core"/"Optional" values are exactly the case that would be lost.
 *
 * Runs on every start and is a no-op once there is nothing left to convert, so
 * it costs one query on a converted database. It never overwrites a category
 * already assigned, which is what makes it safe to leave in place after an
 * operator has since renamed or merged the categories by hand.
 */
@Component
@Order(20)
public class SubjectCategoryBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubjectCategoryBackfill.class);

    /**
     * Ordering for the names the application shipped with, so a converted
     * database gets the bands in curriculum order rather than alphabetically.
     * Anything not listed sorts after these, by name.
     */
    private static final List<String> KNOWN_ORDER = List.of(
            "Core", "Category 1", "Category 2", "Category 3",
            "Optional", "Language", "Aesthetic");

    private final SubjectDetailDao subjectDao;
    private final SubjectCategoryDao categoryDao;

    public SubjectCategoryBackfill(SubjectDetailDao subjectDao, SubjectCategoryDao categoryDao) {
        this.subjectDao = subjectDao;
        this.categoryDao = categoryDao;
    }

    @Override
    @Transactional
    @SuppressWarnings("deprecation")
    public void run(ApplicationArguments args) {

        List<SubjectDetail> pending = subjectDao.findAll().stream()
                .filter(subject -> subject.getCategory() == null)
                .filter(subject -> subject.getLegacyCategory() != null && !subject.getLegacyCategory().isBlank())
                .toList();

        if (pending.isEmpty()) {
            return;
        }

        // Resolve every distinct name once; several subjects share a category.
        Map<String, SubjectCategory> resolved = new LinkedHashMap<>();
        for (SubjectDetail subject : pending) {
            String name = subject.getLegacyCategory().trim();
            resolved.computeIfAbsent(name, this::findOrCreate);
            subject.setCategory(resolved.get(name));
        }

        subjectDao.saveAll(pending);

        log.info("Subject categories: converted {} subject(s) onto {} category row(s) {}.",
                pending.size(), resolved.size(), resolved.keySet());
    }

    private SubjectCategory findOrCreate(String name) {
        return categoryDao.findByName(name).orElseGet(() -> {
            SubjectCategory created = new SubjectCategory();
            created.setName(name);
            created.setSortOrder(sortOrderFor(name));
            created.setActive(Boolean.TRUE);
            return categoryDao.save(created);
        });
    }

    /** Known names keep their curriculum order; the rest go after them. */
    private int sortOrderFor(String name) {
        int known = KNOWN_ORDER.indexOf(name);
        return known >= 0 ? known : KNOWN_ORDER.size();
    }
}
