-- ---------------------------------------------------------------------------
-- Repairs duplicated lookup rows.
--
-- Symptom: the reference tables hold each row twice - two "Active" statuses,
-- two "Teacher" designations, two "Grade 1"s. It happens when the block of
-- INSERT statements the README used to carry is pasted in more than once.
--
-- Why it has to be fixed rather than tolerated:
--
--   * The seed scripts look a row up by name, e.g.
--     (SELECT id FROM status WHERE name = 'Active'). Two matches makes that
--     "Subquery returns more than 1 row" and the script stops halfway.
--   * The application does the same thing through Spring Data - DAO methods
--     like RegistrationStatusDao.getByName return one entity, and throw
--     NonUniqueResultException when the table holds two.
--   * Two rows named "Grade 1" means two sets of classes named Grade 1, and
--     every report splits its figures across both.
--
-- Run order:
--
--   1. Start the API once, so Hibernate creates the newer tables
--      (./gradlew bootRun, then stop it).
--   2. mysql -u root -p scbc < scbck/seed/deduplicate-lookups.sql
--   3. mysql -u root -p scbc < scbck/seed/academic-seed.sql
--
-- Step 1 matters: this script repoints foreign keys in classroom and
-- grade_head, which do not exist until the API has started.
--
-- What it does: for each lookup table, the LOWEST id for a given name wins.
-- Every row pointing at one of the losers is repointed to the winner, then the
-- losers are deleted. Nothing that references a lookup is lost.
--
-- BACK UP FIRST. One line is enough:
--   docker exec mysql mysqldump -u root -p scbc > scbc-backup.sql
--
-- Re-runnable: on a clean database every statement below matches nothing.
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 1. Repoint everything that references a duplicate
--
-- Each statement reads "find the name of the row I currently point at, then
-- point at the lowest-numbered row carrying that name instead".
-- ---------------------------------------------------------------------------

-- role <- designation, privilage, user_has_role
UPDATE designation
SET role_id = (
  SELECT MIN(target.id) FROM role target
  WHERE target.name = (SELECT source.name FROM role source WHERE source.id = designation.role_id))
WHERE role_id IS NOT NULL;

UPDATE privilage
SET role_id = (
  SELECT MIN(target.id) FROM role target
  WHERE target.name = (SELECT source.name FROM role source WHERE source.id = privilage.role_id))
WHERE role_id IS NOT NULL;

UPDATE user_has_role
SET role_id = (
  SELECT MIN(target.id) FROM role target
  WHERE target.name = (SELECT source.name FROM role source WHERE source.id = user_has_role.role_id))
WHERE role_id IS NOT NULL;

-- module <- privilage
UPDATE privilage
SET module_id = (
  SELECT MIN(target.id) FROM module target
  WHERE target.name = (SELECT source.name FROM module source WHERE source.id = privilage.module_id))
WHERE module_id IS NOT NULL;

-- status <- employee
UPDATE employee
SET status_id = (
  SELECT MIN(target.id) FROM status target
  WHERE target.name = (SELECT source.name FROM status source WHERE source.id = employee.status_id))
WHERE status_id IS NOT NULL;

-- designation <- employee
UPDATE employee
SET designation_id = (
  SELECT MIN(target.id) FROM designation target
  WHERE target.name = (SELECT source.name FROM designation source
                       WHERE source.id = employee.designation_id))
WHERE designation_id IS NOT NULL;

-- student_status <- student
UPDATE student
SET student_status_id = (
  SELECT MIN(target.id) FROM student_status target
  WHERE target.name = (SELECT source.name FROM student_status source
                       WHERE source.id = student.student_status_id))
WHERE student_status_id IS NOT NULL;

-- grade <- student, classroom, grade_head
UPDATE student
SET grade_id = (
  SELECT MIN(target.id) FROM grade target
  WHERE target.name = (SELECT source.name FROM grade source WHERE source.id = student.grade_id))
WHERE grade_id IS NOT NULL;

UPDATE classroom
SET grade_id = (
  SELECT MIN(target.id) FROM grade target
  WHERE target.name = (SELECT source.name FROM grade source WHERE source.id = classroom.grade_id))
WHERE grade_id IS NOT NULL;

UPDATE grade_head
SET grade_id = (
  SELECT MIN(target.id) FROM grade target
  WHERE target.name = (SELECT source.name FROM grade source WHERE source.id = grade_head.grade_id))
WHERE grade_id IS NOT NULL;


-- ---------------------------------------------------------------------------
-- 2. Drop the join-table rows that repointing has just made identical
--
-- If a user held both "Teacher" rows, they now hold "Teacher" twice.
-- ---------------------------------------------------------------------------

-- user_has_role is a pure join table with no id of its own, so there is no
-- "keep the lowest" to express. It is rebuilt from its own distinct rows
-- instead, which comes to the same thing.
DROP TABLE IF EXISTS dedupe_user_roles;

CREATE TEMPORARY TABLE dedupe_user_roles AS (
  SELECT DISTINCT user_id, role_id FROM user_has_role);

DELETE FROM user_has_role;

INSERT INTO user_has_role (user_id, role_id)
SELECT user_id, role_id FROM dedupe_user_roles;

DROP TABLE IF EXISTS dedupe_user_roles;

DELETE FROM privilage
WHERE id NOT IN (
  SELECT keep FROM (
    SELECT MIN(id) AS keep FROM privilage GROUP BY role_id, module_id
  ) AS survivors);


-- ---------------------------------------------------------------------------
-- 3. Delete the duplicate lookup rows
--
-- The extra derived table around each subquery is not decoration: MySQL
-- refuses a subquery that reads the same table a DELETE is targeting, and
-- wrapping it forces the result to be materialised first.
-- ---------------------------------------------------------------------------

DELETE FROM role
WHERE id NOT IN (SELECT keep FROM (SELECT MIN(id) AS keep FROM role GROUP BY name) AS survivors);

DELETE FROM status
WHERE id NOT IN (SELECT keep FROM (SELECT MIN(id) AS keep FROM status GROUP BY name) AS survivors);

DELETE FROM student_status
WHERE id NOT IN (
  SELECT keep FROM (SELECT MIN(id) AS keep FROM student_status GROUP BY name) AS survivors);

DELETE FROM designation
WHERE id NOT IN (
  SELECT keep FROM (SELECT MIN(id) AS keep FROM designation GROUP BY name) AS survivors);

DELETE FROM grade
WHERE id NOT IN (SELECT keep FROM (SELECT MIN(id) AS keep FROM grade GROUP BY name) AS survivors);

DELETE FROM module
WHERE id NOT IN (SELECT keep FROM (SELECT MIN(id) AS keep FROM module GROUP BY name) AS survivors);

DELETE FROM academic_year
WHERE id NOT IN (
  SELECT keep FROM (SELECT MIN(id) AS keep FROM academic_year GROUP BY name) AS survivors);

DELETE FROM registration_status
WHERE id NOT IN (
  SELECT keep FROM (SELECT MIN(id) AS keep FROM registration_status GROUP BY name) AS survivors);

DELETE FROM payment_type
WHERE id NOT IN (
  SELECT keep FROM (SELECT MIN(id) AS keep FROM payment_type GROUP BY name) AS survivors);

DELETE FROM subject_detail
WHERE id NOT IN (
  SELECT keep FROM (SELECT MIN(id) AS keep FROM subject_detail GROUP BY name) AS survivors);


-- ---------------------------------------------------------------------------
-- 4. Check
--
-- Every count below should come back 0. Anything else means a name is still
-- carried by more than one row, and the seeds will fail on it.
-- ---------------------------------------------------------------------------

SELECT 'role' AS lookup_table, COUNT(*) AS duplicate_names FROM (
  SELECT name FROM role GROUP BY name HAVING COUNT(*) > 1) AS d
UNION ALL SELECT 'status', COUNT(*) FROM (
  SELECT name FROM status GROUP BY name HAVING COUNT(*) > 1) AS d
UNION ALL SELECT 'student_status', COUNT(*) FROM (
  SELECT name FROM student_status GROUP BY name HAVING COUNT(*) > 1) AS d
UNION ALL SELECT 'designation', COUNT(*) FROM (
  SELECT name FROM designation GROUP BY name HAVING COUNT(*) > 1) AS d
UNION ALL SELECT 'grade', COUNT(*) FROM (
  SELECT name FROM grade GROUP BY name HAVING COUNT(*) > 1) AS d
UNION ALL SELECT 'module', COUNT(*) FROM (
  SELECT name FROM module GROUP BY name HAVING COUNT(*) > 1) AS d;
