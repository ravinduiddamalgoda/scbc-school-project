-- ---------------------------------------------------------------------------
-- Reference data the application needs to run: roles, statuses, designations,
-- privilege modules, grades, subjects, an academic year with terms, and one
-- class per grade.
--
-- Run it once, after the API has started for the first time (Hibernate creates
-- the new tables on start-up with ddl-auto=update).
--
--   mysql -u root -p scbc < scbck/seed/academic-seed.sql
--
-- Every statement is written to be re-runnable: a second run inserts nothing
-- rather than duplicating the rows.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Base lookups
--
-- These four used to be a block of SQL in the README for the operator to paste
-- by hand. They are here instead because everything below depends on them: a
-- database missing the "Teacher" designation, for instance, silently produces
-- a school with no teachers rather than an error.
-- ---------------------------------------------------------------------------

INSERT INTO role (name)
SELECT wanted.name FROM (
  SELECT 'Admin' AS name UNION ALL SELECT 'Principal'
  UNION ALL SELECT 'Teacher' UNION ALL SELECT 'Clerk'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.name = wanted.name);

-- Employee status. Id 3, "Deleted", is what a soft-deleted employee moves to.
INSERT INTO status (name)
SELECT wanted.name FROM (
  SELECT 'Active' AS name UNION ALL SELECT 'Inactive' UNION ALL SELECT 'Deleted'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM status s WHERE s.name = wanted.name);

INSERT INTO student_status (name)
SELECT wanted.name FROM (
  SELECT 'Active' AS name UNION ALL SELECT 'Suspended' UNION ALL SELECT 'Deleted'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM student_status s WHERE s.name = wanted.name);

-- Designations carry the role a staff member's login gets, looked up by name
-- rather than by a hard-coded id.
INSERT INTO designation (name, role_id, user_account)
SELECT wanted.name, r.id, wanted.user_account
FROM (
  SELECT 'Principal' AS name, 'Principal' AS role_name, TRUE AS user_account
  UNION ALL SELECT 'Teacher', 'Teacher', TRUE
  UNION ALL SELECT 'Clerk', 'Clerk', FALSE
) AS wanted
JOIN role r ON r.name = wanted.role_name
WHERE NOT EXISTS (SELECT 1 FROM designation d WHERE d.name = wanted.name);

-- The modules the original application shipped with.
INSERT INTO module (name)
SELECT wanted.name FROM (
  SELECT 'Employee' AS name UNION ALL SELECT 'Student' UNION ALL SELECT 'Guardian'
  UNION ALL SELECT 'User' UNION ALL SELECT 'Privilage'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM module m WHERE m.name = wanted.name);

-- Privilege modules for the newer screens. Without these rows the permission
-- matrix has nothing to grant, and only Admin can reach them.
INSERT INTO module (name)
SELECT wanted.name FROM (
  SELECT 'Subject' AS name UNION ALL SELECT 'Class' UNION ALL SELECT 'Report'
  UNION ALL SELECT 'Attendance' UNION ALL SELECT 'Payment'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM module m WHERE m.name = wanted.name);

-- How a payment was received. The Fees Details report prints this as "Method".
INSERT INTO payment_type (name)
SELECT wanted.name FROM (
  SELECT 'Cash' AS name UNION ALL SELECT 'Bank deposit'
  UNION ALL SELECT 'Cheque' UNION ALL SELECT 'Online'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM payment_type t WHERE t.name = wanted.name);

-- Enrolment states. Only "Active" is counted by the student-count reports, so
-- a student who transfers out stops inflating their old class without the
-- record being destroyed.
INSERT INTO registration_status (name)
SELECT wanted.name FROM (
  SELECT 'Active' AS name UNION ALL SELECT 'Transferred' UNION ALL SELECT 'Cancelled'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM registration_status s WHERE s.name = wanted.name);

-- Grades 1 to 13. The reports read the number out of the name to decide which
-- band a grade prints in, so keep the "Grade N" form.
INSERT INTO grade (name)
SELECT CONCAT('Grade ', levels.n) FROM (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13
) AS levels
WHERE NOT EXISTS (SELECT 1 FROM grade g WHERE g.name = CONCAT('Grade ', levels.n));

-- The academic year every class, timetable line and enrolment hangs off.
-- Adjust the name and dates before running.
INSERT INTO academic_year (name, start_date, end_date, current_year)
SELECT '2026', '2026-01-05', '2026-12-11', 1
WHERE NOT EXISTS (SELECT 1 FROM academic_year y WHERE y.name = '2026');

-- Terms. The Week Attendance report is a per-term breakdown, and these dates
-- are what it counts school days between. They may not overlap.
INSERT INTO term (name, start_date, end_date, academic_year_id)
SELECT wanted.name, wanted.start_date, wanted.end_date, y.id
FROM (
  SELECT 'First Term'  AS name, '2026-01-05' AS start_date, '2026-04-03' AS end_date
  UNION ALL SELECT 'Second Term', '2026-04-27', '2026-08-07'
  UNION ALL SELECT 'Third Term',  '2026-09-07', '2026-12-11'
) AS wanted
CROSS JOIN (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1) AS y
WHERE NOT EXISTS (
  SELECT 1 FROM term t WHERE t.academic_year_id = y.id AND t.name = wanted.name);

-- ---------------------------------------------------------------------------
-- Subjects
--
-- `code` is the column heading the reports use when the full name will not fit
-- across a thirty-column matrix. `category` groups the columns so a band's
-- subjects read the way the timetable does.
-- ---------------------------------------------------------------------------
INSERT INTO subject_detail (name, code, category, active)
SELECT wanted.name, wanted.code, wanted.category, 1 FROM (
  SELECT 'Buddhism'           AS name, NULL          AS code, 'Core'        AS category
  UNION ALL SELECT 'Sinhala',            NULL,          'Core'
  UNION ALL SELECT 'History',            NULL,          'Core'
  UNION ALL SELECT 'Maths',              NULL,          'Core'
  UNION ALL SELECT 'Science',            NULL,          'Core'
  UNION ALL SELECT 'English',            NULL,          'Core'
  UNION ALL SELECT 'Civics',             NULL,          'Optional'
  UNION ALL SELECT 'PTS',                NULL,          'Optional'
  UNION ALL SELECT 'ICT',                NULL,          'Optional'
  UNION ALL SELECT 'Health',             NULL,          'Optional'
  UNION ALL SELECT 'Tamil',              NULL,          'Optional'
  UNION ALL SELECT 'Geography',          'Geog.',       'Optional'
  UNION ALL SELECT 'Art',                NULL,          'Aesthetic'
  UNION ALL SELECT 'Music',              NULL,          'Aesthetic'
  UNION ALL SELECT 'Dancing',            NULL,          'Aesthetic'
  UNION ALL SELECT 'Drama',              NULL,          'Aesthetic'
  UNION ALL SELECT 'Media',              NULL,          'Aesthetic'
  UNION ALL SELECT 'Chinese',            NULL,          'Language'
  UNION ALL SELECT 'Japanese',           NULL,          'Language'
  UNION ALL SELECT 'Korean',             NULL,          'Language'
  UNION ALL SELECT 'English Literature', 'Eng. Lit.',   'Language'
  UNION ALL SELECT 'Physics',            NULL,          'A/L Science'
  UNION ALL SELECT 'Chemistry',          NULL,          'A/L Science'
  UNION ALL SELECT 'Biology',            NULL,          'A/L Science'
  UNION ALL SELECT 'Combined Maths',     'Comb. Maths', 'A/L Science'
  UNION ALL SELECT 'Agriculture',        'Agri.',       'A/L Science'
  UNION ALL SELECT 'Accounts',           NULL,          'A/L Commerce'
  UNION ALL SELECT 'Business',           NULL,          'A/L Commerce'
  UNION ALL SELECT 'Economics',          'Econ',        'A/L Commerce'
) AS wanted
WHERE NOT EXISTS (SELECT 1 FROM subject_detail s WHERE s.name = wanted.name);

-- ---------------------------------------------------------------------------
-- Classes
--
-- Grades 1 to 11 get classes A to G; grades 12 and 13 get the four A/L streams
-- in place of a letter, which is how the report spreadsheets label them.
--
-- Every class is seeded Sinhala medium because most of them are; change the
-- English-medium ones on the Classes screen and the Medium wise Student Count
-- report follows immediately.
--
-- Class teachers and timetables are deliberately left empty - assign them on
-- the Classes screen, where the reports pick them up immediately.
-- ---------------------------------------------------------------------------
INSERT INTO classroom (name, grade_id, academic_year_id, medium)
SELECT letters.name, g.id, y.id, 'Sinhala'
FROM grade g
CROSS JOIN (
  SELECT 'A' AS name UNION ALL SELECT 'B' UNION ALL SELECT 'C' UNION ALL SELECT 'D'
  UNION ALL SELECT 'E' UNION ALL SELECT 'F' UNION ALL SELECT 'G'
) AS letters
CROSS JOIN (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1) AS y
WHERE g.name IN ('Grade 1', 'Grade 2', 'Grade 3', 'Grade 4', 'Grade 5', 'Grade 6',
                 'Grade 7', 'Grade 8', 'Grade 9', 'Grade 10', 'Grade 11')
  AND NOT EXISTS (
    SELECT 1 FROM classroom c
    WHERE c.grade_id = g.id AND c.academic_year_id = y.id AND c.name = letters.name);

INSERT INTO classroom (name, grade_id, academic_year_id, medium)
SELECT streams.name, g.id, y.id, 'Sinhala'
FROM grade g
CROSS JOIN (
  SELECT 'MATHS' AS name UNION ALL SELECT 'BIO/MATHS'
  UNION ALL SELECT 'COMMERCE' UNION ALL SELECT 'ARTS'
) AS streams
CROSS JOIN (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1) AS y
WHERE g.name IN ('Grade 12', 'Grade 13')
  AND NOT EXISTS (
    SELECT 1 FROM classroom c
    WHERE c.grade_id = g.id AND c.academic_year_id = y.id AND c.name = streams.name);
