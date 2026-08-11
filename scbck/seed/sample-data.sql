-- ---------------------------------------------------------------------------
-- Sample data for every table the application maps.
--
-- This is DEMO data. It exists so that a fresh install shows all nine reports
-- with plausible figures instead of nine empty pages. Do not run it on a
-- database holding real records.
--
--   mysql -u root -p scbc < scbck/seed/academic-seed.sql   -- run this FIRST
--   mysql -u root -p scbc < scbck/seed/sample-data.sql
--
-- It depends on academic-seed.sql for the grades, subjects, academic year,
-- terms and classes. Run without it, every statement below simply inserts
-- nothing - the joins find no rows rather than failing halfway.
--
-- Re-runnable: every statement is guarded, so a second run inserts nothing.
--
-- To undo it, see the "Removing the sample data" section at the foot of the
-- file.
--
-- What it creates, for the academic year 2026:
--
--   100 employees              2,808 students across 85 classes of 28 to 38
--   1,404 guardians            85 class teachers, 13 grade heads
--   876 timetable lines        24,024 subject enrolments
--   1,275 registers (15 school days x 85 classes), 41,700 marks
--   5,616 fee receipts, two per student
--   20 privilege rows and 3 demo login accounts
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 0. Helper tables
--
-- Temporary, so they vanish with the session and never reach the schema. They
-- are dropped first in case a previous run stopped halfway.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS seed_numbers;
DROP TABLE IF EXISTS seed_first_names;
DROP TABLE IF EXISTS seed_surnames;
DROP TABLE IF EXISTS seed_days;
DROP TABLE IF EXISTS seed_curriculum;
DROP TABLE IF EXISTS seed_privileges;
DROP TABLE IF EXISTS seed_grades;
DROP TABLE IF EXISTS seed_classes;
DROP TABLE IF EXISTS seed_roll;
DROP TABLE IF EXISTS seed_teachers;

-- 1 to 40: the largest class this script generates.
CREATE TEMPORARY TABLE seed_numbers AS (
  SELECT (tens.n * 10 + units.n + 1) AS n
  FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS tens
  CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
  ) AS units
);

CREATE TEMPORARY TABLE seed_first_names AS (
  SELECT 1 AS n, 'Amara' AS name UNION ALL SELECT 2, 'Bimal' UNION ALL SELECT 3, 'Chathuri'
  UNION ALL SELECT 4, 'Dilhani' UNION ALL SELECT 5, 'Eranga' UNION ALL SELECT 6, 'Fathima'
  UNION ALL SELECT 7, 'Gayan' UNION ALL SELECT 8, 'Hasini' UNION ALL SELECT 9, 'Ishara'
  UNION ALL SELECT 10, 'Janaka' UNION ALL SELECT 11, 'Kavindu' UNION ALL SELECT 12, 'Lakmini'
  UNION ALL SELECT 13, 'Malith' UNION ALL SELECT 14, 'Nadeesha' UNION ALL SELECT 15, 'Oshadi'
  UNION ALL SELECT 16, 'Pasindu' UNION ALL SELECT 17, 'Ruwani' UNION ALL SELECT 18, 'Sanduni'
  UNION ALL SELECT 19, 'Tharindu' UNION ALL SELECT 20, 'Upeksha' UNION ALL SELECT 21, 'Vihanga'
  UNION ALL SELECT 22, 'Wasana' UNION ALL SELECT 23, 'Yasiru'
);

CREATE TEMPORARY TABLE seed_surnames AS (
  SELECT 1 AS n, 'Perera' AS name UNION ALL SELECT 2, 'Silva' UNION ALL SELECT 3, 'Fernando'
  UNION ALL SELECT 4, 'Bandara' UNION ALL SELECT 5, 'Rajapaksha' UNION ALL SELECT 6, 'Wickramasinghe'
  UNION ALL SELECT 7, 'Jayawardena' UNION ALL SELECT 8, 'Gunasekara' UNION ALL SELECT 9, 'Dissanayake'
  UNION ALL SELECT 10, 'Ekanayake' UNION ALL SELECT 11, 'Rathnayake' UNION ALL SELECT 12, 'Herath'
  UNION ALL SELECT 13, 'Weerasinghe' UNION ALL SELECT 14, 'Senanayake' UNION ALL SELECT 15, 'Kumarasiri'
  UNION ALL SELECT 16, 'Abeywardena' UNION ALL SELECT 17, 'Nanayakkara' UNION ALL SELECT 18, 'Samarasinghe'
  UNION ALL SELECT 19, 'Liyanage' UNION ALL SELECT 20, 'Wijesekara' UNION ALL SELECT 21, 'Karunaratne'
);

-- Three full school weeks in March 2026, weekends left out. Every date here
-- becomes a register, and the reports count "days conducted" from exactly
-- these rows - a holiday is simply a date this list does not mention.
CREATE TEMPORARY TABLE seed_days AS (
  SELECT DATE '2026-03-02' AS d UNION ALL SELECT DATE '2026-03-03'
  UNION ALL SELECT DATE '2026-03-04' UNION ALL SELECT DATE '2026-03-05'
  UNION ALL SELECT DATE '2026-03-06' UNION ALL SELECT DATE '2026-03-09'
  UNION ALL SELECT DATE '2026-03-10' UNION ALL SELECT DATE '2026-03-11'
  UNION ALL SELECT DATE '2026-03-12' UNION ALL SELECT DATE '2026-03-13'
  UNION ALL SELECT DATE '2026-03-16' UNION ALL SELECT DATE '2026-03-17'
  UNION ALL SELECT DATE '2026-03-18' UNION ALL SELECT DATE '2026-03-19'
  UNION ALL SELECT DATE '2026-03-20'
);

-- Which subjects each part of the school is taught. `stream` is null except
-- for the A/L classes, where the class name is the stream.
CREATE TEMPORARY TABLE seed_curriculum AS (
  -- Primary, grades 1 to 5
  SELECT 1 AS level_from, 5 AS level_to, CAST(NULL AS CHAR(20)) AS stream, 'Buddhism' AS subject
  UNION ALL SELECT 1, 5, NULL, 'Sinhala'   UNION ALL SELECT 1, 5, NULL, 'Maths'
  UNION ALL SELECT 1, 5, NULL, 'English'   UNION ALL SELECT 1, 5, NULL, 'Science'
  UNION ALL SELECT 1, 5, NULL, 'Art'       UNION ALL SELECT 1, 5, NULL, 'Music'
  UNION ALL SELECT 1, 5, NULL, 'Dancing'

  -- Junior secondary, grades 6 to 9
  UNION ALL SELECT 6, 9, NULL, 'Buddhism'  UNION ALL SELECT 6, 9, NULL, 'Sinhala'
  UNION ALL SELECT 6, 9, NULL, 'History'   UNION ALL SELECT 6, 9, NULL, 'Maths'
  UNION ALL SELECT 6, 9, NULL, 'Science'   UNION ALL SELECT 6, 9, NULL, 'English'
  UNION ALL SELECT 6, 9, NULL, 'Civics'    UNION ALL SELECT 6, 9, NULL, 'PTS'
  UNION ALL SELECT 6, 9, NULL, 'ICT'       UNION ALL SELECT 6, 9, NULL, 'Health'
  UNION ALL SELECT 6, 9, NULL, 'Tamil'     UNION ALL SELECT 6, 9, NULL, 'Geography'
  UNION ALL SELECT 6, 9, NULL, 'Art'       UNION ALL SELECT 6, 9, NULL, 'Music'
  UNION ALL SELECT 6, 9, NULL, 'Dancing'

  -- O/L, grades 10 and 11
  UNION ALL SELECT 10, 11, NULL, 'Buddhism' UNION ALL SELECT 10, 11, NULL, 'Sinhala'
  UNION ALL SELECT 10, 11, NULL, 'History'  UNION ALL SELECT 10, 11, NULL, 'Maths'
  UNION ALL SELECT 10, 11, NULL, 'Science'  UNION ALL SELECT 10, 11, NULL, 'English'
  UNION ALL SELECT 10, 11, NULL, 'ICT'      UNION ALL SELECT 10, 11, NULL, 'Geography'
  UNION ALL SELECT 10, 11, NULL, 'Art'      UNION ALL SELECT 10, 11, NULL, 'Drama'

  -- A/L, grades 12 and 13, by stream
  UNION ALL SELECT 12, 13, 'MATHS', 'Combined Maths'
  UNION ALL SELECT 12, 13, 'MATHS', 'Physics'
  UNION ALL SELECT 12, 13, 'MATHS', 'Chemistry'
  UNION ALL SELECT 12, 13, 'MATHS', 'ICT'
  UNION ALL SELECT 12, 13, 'BIO/MATHS', 'Biology'
  UNION ALL SELECT 12, 13, 'BIO/MATHS', 'Physics'
  UNION ALL SELECT 12, 13, 'BIO/MATHS', 'Chemistry'
  UNION ALL SELECT 12, 13, 'BIO/MATHS', 'Agriculture'
  UNION ALL SELECT 12, 13, 'COMMERCE', 'Accounts'
  UNION ALL SELECT 12, 13, 'COMMERCE', 'Business'
  UNION ALL SELECT 12, 13, 'COMMERCE', 'Economics'
  UNION ALL SELECT 12, 13, 'COMMERCE', 'ICT'
  UNION ALL SELECT 12, 13, 'ARTS', 'Geography'
  UNION ALL SELECT 12, 13, 'ARTS', 'Sinhala'
  UNION ALL SELECT 12, 13, 'ARTS', 'Economics'
  UNION ALL SELECT 12, 13, 'ARTS', 'Media'
  UNION ALL SELECT 12, 13, 'ARTS', 'Art'
  UNION ALL SELECT 12, 13, 'ARTS', 'Dancing'
);


-- ---------------------------------------------------------------------------
-- 1. Employees
--
-- 100 staff, which is roughly what 85 classes needs. Numbers are all derived
-- from the row index so a re-run produces exactly the same people.
-- ---------------------------------------------------------------------------

INSERT INTO employee (emp_no, fullname, callingname, nic, gender, dob, email,
                      civilstatus, mobileno, address, status_id, designation_id, added_datetime)
SELECT
  LPAD(idx, 8, '0'),
  CONCAT(f.name, ' ', s.name),
  f.name,
  CONCAT('19', LPAD(idx, 10, '0')),
  CASE WHEN MOD(idx, 3) = 0 THEN 'Male' ELSE 'Female' END,
  DATE '1985-01-01',
  CONCAT('staff', LPAD(idx, 3, '0'), '@scbc.lk'),
  CASE WHEN MOD(idx, 4) = 0 THEN 'Single' ELSE 'Married' END,
  CONCAT('077', LPAD(idx, 7, '0')),
  CONCAT(idx, ', Temple Road, Kandy'),
  (SELECT id FROM status WHERE name = 'Active' ORDER BY id LIMIT 1),
  -- One principal, the rest teachers apart from a handful of clerks.
  CASE
    WHEN idx = 1 THEN (SELECT id FROM designation WHERE name = 'Principal' ORDER BY id LIMIT 1)
    WHEN idx > 96 THEN (SELECT id FROM designation WHERE name = 'Clerk' ORDER BY id LIMIT 1)
    ELSE (SELECT id FROM designation WHERE name = 'Teacher' ORDER BY id LIMIT 1)
  END,
  TIMESTAMP '2026-01-02 08:00:00'
FROM (
  SELECT (t.n * 10 + u.n + 1) AS idx
  FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS t
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
              UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS u
) AS seq
JOIN seed_first_names f ON f.n = MOD(seq.idx * 5, 23) + 1
JOIN seed_surnames s ON s.n = MOD(seq.idx * 3, 21) + 1
WHERE NOT EXISTS (SELECT 1 FROM employee e WHERE e.emp_no = LPAD(seq.idx, 8, '0'));

-- Teachers numbered 1..N, used below to spread class and subject duties.
--
-- `total` repeats the headcount on every row so the statements below can wrap
-- around the list with a single mention of this table. MySQL refuses to open a
-- temporary table twice in one query (error 1137 "Can't reopen table"), so the
-- obvious "(SELECT COUNT(*) FROM seed_teachers)" beside a join to it does not
-- run - even though H2 accepts it.
CREATE TEMPORARY TABLE seed_teachers AS (
  SELECT e.id,
         ROW_NUMBER() OVER (ORDER BY e.emp_no) AS n,
         COUNT(*) OVER () AS total
  FROM employee e
  WHERE e.designation_id = (SELECT id FROM designation WHERE name = 'Teacher' ORDER BY id LIMIT 1)
);


-- ---------------------------------------------------------------------------
-- 2. Class structure: which grade each class belongs to, and how big it is
--
-- Class sizes vary between 28 and 38 the way the source workbooks do, rather
-- than every class holding the same number - a report where all 85 figures
-- matched would not tell anyone anything.
-- ---------------------------------------------------------------------------

CREATE TEMPORARY TABLE seed_grades AS (
  SELECT g.id, CAST(REPLACE(g.name, 'Grade ', '') AS DECIMAL(10, 0)) AS level
  FROM grade g
  WHERE g.name LIKE 'Grade %'
);

CREATE TEMPORARY TABLE seed_classes AS (
  SELECT c.id, c.name, sg.level,
         ROW_NUMBER() OVER (ORDER BY sg.level, c.name) AS n,
         28 + MOD(c.id * 7, 11) AS class_size
  FROM classroom c
  JOIN seed_grades sg ON sg.id = c.grade_id
  WHERE c.academic_year_id = (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1)
);

-- A seat for every student the school will have, numbered globally. This is
-- what ties a student to a class: both the student insert and the enrolment
-- insert below read the same seat numbers.
CREATE TEMPORARY TABLE seed_roll AS (
  SELECT sc.id AS classroom_id, sc.level, seat.n AS seat_no,
         ROW_NUMBER() OVER (ORDER BY sc.n, seat.n) AS idx
  FROM seed_classes sc
  JOIN seed_numbers seat ON seat.n <= sc.class_size
);


-- ---------------------------------------------------------------------------
-- 3. Class teachers and grade heads
-- ---------------------------------------------------------------------------

-- No table alias in these statements: MySQL and H2 disagree about qualifying a
-- column on the left of SET, and unaliased works in both.
UPDATE classroom
SET employee_id = (
  SELECT t.id FROM seed_teachers t
  JOIN seed_classes sc ON sc.id = classroom.id
  WHERE t.n = MOD(sc.n, t.total) + 1
)
WHERE employee_id IS NULL
  AND academic_year_id = (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1);

-- Two classes per grade are English medium from Grade 6 up, which gives the
-- Medium wise report something to show besides a single column.
UPDATE classroom
SET medium = 'English'
WHERE academic_year_id = (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1)
  AND name IN ('F', 'G')
  AND grade_id IN (SELECT id FROM seed_grades WHERE level BETWEEN 6 AND 11);

INSERT INTO grade_head (grade_id, academic_year_id, employee_id)
SELECT sg.id, y.id, t.id
FROM seed_grades sg
CROSS JOIN (SELECT id FROM academic_year WHERE name = '2026' ORDER BY id LIMIT 1) AS y
JOIN seed_teachers t ON t.n = MOD(sg.level * 6, t.total) + 1
WHERE NOT EXISTS (
  SELECT 1 FROM grade_head h WHERE h.grade_id = sg.id AND h.academic_year_id = y.id);


-- ---------------------------------------------------------------------------
-- 4. Timetables
--
-- The subject teacher is chosen from the subject and the grade, not the class,
-- so one teacher takes a subject across all seven classes of a grade. That is
-- deliberate: the Subject Wise Teachers report counts distinct people, and it
-- should read 1 or 2 per grade rather than 7.
-- ---------------------------------------------------------------------------

INSERT INTO classroom_subject (classroom_id, subject_detail_id, employee_id)
SELECT sc.id, sd.id, t.id
FROM seed_classes sc
JOIN seed_curriculum cur
  ON sc.level BETWEEN cur.level_from AND cur.level_to
 AND (cur.stream IS NULL OR cur.stream = sc.name)
JOIN subject_detail sd ON sd.name = cur.subject
JOIN seed_teachers t
  ON t.n = MOD(sd.id * 3 + sc.level * 11, t.total) + 1
WHERE NOT EXISTS (
  SELECT 1 FROM classroom_subject cs
  WHERE cs.classroom_id = sc.id AND cs.subject_detail_id = sd.id);


-- ---------------------------------------------------------------------------
-- 5. Guardians and students
--
-- Two students share a guardian where the numbering works out, which is what a
-- roll with siblings on it looks like.
-- ---------------------------------------------------------------------------

INSERT INTO guardian (guardian_no, fullname, nic, mobile, email, occupation, employer,
                      address, relationship)
SELECT
  LPAD(g.idx, 8, '0'),
  CONCAT(f.name, ' ', s.name),
  CONCAT('20', LPAD(g.idx, 10, '0')),
  CONCAT('071', LPAD(g.idx, 7, '0')),
  CONCAT('guardian', LPAD(g.idx, 5, '0'), '@example.lk'),
  CASE MOD(g.idx, 5)
    WHEN 0 THEN 'Teacher' WHEN 1 THEN 'Farmer' WHEN 2 THEN 'Driver'
    WHEN 3 THEN 'Shop owner' ELSE 'Government officer' END,
  'Self employed',
  CONCAT(g.idx, ', Lake Road, Kandy'),
  CASE WHEN MOD(g.idx, 2) = 0 THEN 'Father' ELSE 'Mother' END
FROM (SELECT DISTINCT FLOOR((idx - 1) / 2) + 1 AS idx FROM seed_roll) AS g
JOIN seed_first_names f ON f.n = MOD(g.idx * 17, 23) + 1
JOIN seed_surnames s ON s.n = MOD(g.idx * 19, 21) + 1
WHERE NOT EXISTS (SELECT 1 FROM guardian gu WHERE gu.guardian_no = LPAD(g.idx, 8, '0'));

INSERT INTO student (stu_no, fullname, callingname, birth_certi_no, gender, dob, religion,
                     nationality, previous_scl, address, student_status_id, grade_id,
                     guardian_id, added_datetime)
SELECT
  LPAD(r.idx, 8, '0'),
  -- Initials plus a name, which is how a Sri Lankan class list reads and what
  -- keeps 2,805 students from sharing a handful of names.
  CONCAT(
    SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZ', MOD(r.idx * 3, 26) + 1, 1), '.',
    SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZ', MOD(r.idx * 7, 26) + 1, 1), '. ',
    f.name, ' ', s.name),
  f.name,
  CONCAT('BC', LPAD(r.idx, 8, '0')),
  CASE WHEN MOD(r.idx, 2) = 0 THEN 'Male' ELSE 'Female' END,
  -- Roughly age-appropriate: a Grade 1 child born in 2020, a Grade 13 in 2008.
  -- Built as a string rather than with date arithmetic, because MySQL and H2
  -- spell interval subtraction differently and this script has to run on both.
  CAST(CONCAT(CAST(2021 - r.level AS DECIMAL(10, 0)), '-05-15') AS DATE),
  'Buddhism',
  'Sinhalese',
  CASE WHEN r.level = 1 THEN 'None' ELSE 'Kandy Primary School' END,
  CONCAT(r.idx, ', Lake Road, Kandy'),
  (SELECT id FROM student_status WHERE name = 'Active' ORDER BY id LIMIT 1),
  (SELECT c.grade_id FROM classroom c WHERE c.id = r.classroom_id),
  (SELECT gu.id FROM guardian gu WHERE gu.guardian_no = LPAD(FLOOR((r.idx - 1) / 2) + 1, 8, '0')),
  TIMESTAMP '2026-01-05 09:00:00'
FROM seed_roll r
JOIN seed_first_names f ON f.n = MOD(r.idx * 11, 23) + 1
JOIN seed_surnames s ON s.n = MOD(r.idx * 13, 21) + 1
WHERE NOT EXISTS (SELECT 1 FROM student st WHERE st.stu_no = LPAD(r.idx, 8, '0'));


-- ---------------------------------------------------------------------------
-- 6. Enrolments
--
-- Every student is placed in the class their seat came from. A handful are
-- marked Transferred rather than Active, so the count reports have something
-- to exclude and the figures are not simply the class size.
-- ---------------------------------------------------------------------------

INSERT INTO student_registration (reg_no, date, total_fee, student_id, classroom_id,
                                  registration_status_id)
SELECT
  LPAD(r.idx, 10, '0'),
  DATE '2026-01-05',
  42000,
  st.id,
  r.classroom_id,
  CASE WHEN MOD(r.idx, 97) = 0
    THEN (SELECT id FROM registration_status WHERE name = 'Transferred' ORDER BY id LIMIT 1)
    ELSE (SELECT id FROM registration_status WHERE name = 'Active' ORDER BY id LIMIT 1)
  END
FROM seed_roll r
JOIN student st ON st.stu_no = LPAD(r.idx, 8, '0')
WHERE NOT EXISTS (
  SELECT 1 FROM student_registration sr
  WHERE sr.student_id = st.id AND sr.classroom_id = r.classroom_id);


-- ---------------------------------------------------------------------------
-- 7. Subject enrolments
--
-- Core subjects are taken by everyone; the aesthetic and language baskets by a
-- rotating subset. Without that split a "students taking this subject" figure
-- would only ever repeat the class size, which is exactly the thing the
-- Subject wise Student Count report exists to disprove.
-- ---------------------------------------------------------------------------

INSERT INTO student_subject (student_registration_id, classroom_subject_id)
SELECT sr.id, cs.id
FROM student_registration sr
JOIN classroom_subject cs ON cs.classroom_id = sr.classroom_id
JOIN subject_detail sd ON sd.id = cs.subject_detail_id
WHERE sr.classroom_id IN (SELECT id FROM seed_classes)
  AND (
    COALESCE(sd.category, '') NOT IN ('Aesthetic', 'Language')
    -- About a third of the class takes any given optional.
    OR MOD(sr.id * 7 + sd.id * 3, 3) = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM student_subject ss
    WHERE ss.student_registration_id = sr.id AND ss.classroom_subject_id = cs.id);


-- ---------------------------------------------------------------------------
-- 8. Attendance
--
-- A register for every class on every school day listed above, then a mark for
-- every student on the roll. Presence is pseudo-random but deterministic, so a
-- re-run produces the same figures; it works out at roughly 91% attendance,
-- varying by student and by day.
-- ---------------------------------------------------------------------------

INSERT INTO attendence (date, classroom_id)
SELECT d.d, sc.id
FROM seed_days d
CROSS JOIN seed_classes sc
WHERE NOT EXISTS (
  SELECT 1 FROM attendence a WHERE a.classroom_id = sc.id AND a.date = d.d);

INSERT INTO student_has_attendence (student_id, attendence_id, attendant)
SELECT sr.student_id, a.id,
       -- Derived from the ids rather than the date: it varies the same way and
       -- avoids a date function the two databases spell differently.
       CASE WHEN MOD(sr.student_id * 13 + a.id * 7, 11) = 0 THEN FALSE ELSE TRUE END
FROM attendence a
JOIN seed_classes sc ON sc.id = a.classroom_id
JOIN student_registration sr ON sr.classroom_id = a.classroom_id
JOIN student st ON st.id = sr.student_id
WHERE sr.registration_status_id = (SELECT id FROM registration_status WHERE name = 'Active' ORDER BY id LIMIT 1)
  AND st.student_status_id <> (SELECT id FROM student_status WHERE name = 'Deleted' ORDER BY id LIMIT 1)
  AND NOT EXISTS (
    SELECT 1 FROM student_has_attendence m
    WHERE m.attendence_id = a.id AND m.student_id = sr.student_id);

-- The ER model's day totals, recomputed from the marks that were just written.
-- No report reads them - the reports count the marks - but leaving them null
-- when the model defines them would be its own kind of lie.
UPDATE attendence
SET total_present = (
      SELECT COUNT(*) FROM student_has_attendence m
      WHERE m.attendence_id = attendence.id AND m.attendant = TRUE),
    total_abscent = (
      SELECT COUNT(*) FROM student_has_attendence m
      WHERE m.attendence_id = attendence.id AND m.attendant = FALSE),
    total_child_count = (
      SELECT COUNT(*) FROM student_has_attendence m WHERE m.attendence_id = attendence.id)
WHERE classroom_id IN (SELECT id FROM seed_classes);


-- ---------------------------------------------------------------------------
-- 9. Fee payments
--
-- Two instalments per student, matching the 21,000 a term the Fees Details
-- workbook shows. The second is left unpaid for a slice of the school, so the
-- balance column is not uniformly zero.
-- ---------------------------------------------------------------------------

INSERT INTO payment (bill_no, amount_paid, amount_due, balance_amount, paid_date,
                     payment_type_id, student_id, student_registration_id)
SELECT
  LPAD(r.idx * 2 - 1, 8, '0'),
  21000, 21000, 0,
  DATE '2026-02-10',
  (SELECT id FROM payment_type WHERE name = 'Cash' ORDER BY id LIMIT 1),
  sr.student_id, sr.id
FROM seed_roll r
JOIN student st ON st.stu_no = LPAD(r.idx, 8, '0')
JOIN student_registration sr ON sr.student_id = st.id AND sr.classroom_id = r.classroom_id
WHERE NOT EXISTS (SELECT 1 FROM payment p WHERE p.bill_no = LPAD(r.idx * 2 - 1, 8, '0'));

INSERT INTO payment (bill_no, amount_paid, amount_due, balance_amount, paid_date,
                     payment_type_id, student_id, student_registration_id)
SELECT
  LPAD(r.idx * 2, 8, '0'),
  CASE WHEN MOD(r.idx, 7) = 0 THEN 10000 ELSE 21000 END,
  21000,
  CASE WHEN MOD(r.idx, 7) = 0 THEN 11000 ELSE 0 END,
  DATE '2026-06-12',
  (SELECT id FROM payment_type WHERE name = 'Bank deposit' ORDER BY id LIMIT 1),
  sr.student_id, sr.id
FROM seed_roll r
JOIN student st ON st.stu_no = LPAD(r.idx, 8, '0')
JOIN student_registration sr ON sr.student_id = st.id AND sr.classroom_id = r.classroom_id
WHERE NOT EXISTS (SELECT 1 FROM payment p WHERE p.bill_no = LPAD(r.idx * 2, 8, '0'));


-- ---------------------------------------------------------------------------
-- 10. The privilege matrix
--
-- What each role may do to each module. Admin is not listed: that account
-- bypasses this table in PrivilegeService, so the system can never lock itself
-- out of privilege administration.
-- ---------------------------------------------------------------------------

CREATE TEMPORARY TABLE seed_privileges AS (
  SELECT 'Principal' AS role_name, 'Employee' AS module_name, TRUE AS s, TRUE AS i, TRUE AS u, FALSE AS d
  UNION ALL SELECT 'Principal', 'Student',    TRUE, TRUE,  TRUE,  TRUE
  UNION ALL SELECT 'Principal', 'Guardian',   TRUE, TRUE,  TRUE,  FALSE
  UNION ALL SELECT 'Principal', 'Class',      TRUE, TRUE,  TRUE,  TRUE
  UNION ALL SELECT 'Principal', 'Subject',    TRUE, TRUE,  TRUE,  TRUE
  UNION ALL SELECT 'Principal', 'Attendance', TRUE, TRUE,  TRUE,  TRUE
  UNION ALL SELECT 'Principal', 'Payment',    TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Principal', 'Report',     TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Principal', 'User',       TRUE, FALSE, FALSE, FALSE

  -- A class teacher marks their register and reads the rest.
  UNION ALL SELECT 'Teacher', 'Student',      TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Teacher', 'Guardian',     TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Teacher', 'Class',        TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Teacher', 'Subject',      TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Teacher', 'Attendance',   TRUE, TRUE,  TRUE,  FALSE
  UNION ALL SELECT 'Teacher', 'Report',       TRUE, FALSE, FALSE, FALSE

  -- The office: admissions and money, no say over the timetable.
  UNION ALL SELECT 'Clerk', 'Student',        TRUE, TRUE,  TRUE,  TRUE
  UNION ALL SELECT 'Clerk', 'Guardian',       TRUE, TRUE,  TRUE,  TRUE
  UNION ALL SELECT 'Clerk', 'Payment',        TRUE, TRUE,  TRUE,  FALSE
  UNION ALL SELECT 'Clerk', 'Class',          TRUE, FALSE, FALSE, FALSE
  UNION ALL SELECT 'Clerk', 'Report',         TRUE, FALSE, FALSE, FALSE
);

INSERT INTO privilage (role_id, module_id, privilage_select, privilage_insert,
                       privilage_update, privilage_delete)
SELECT r.id, m.id, p.s, p.i, p.u, p.d
FROM seed_privileges p
JOIN role r ON r.name = p.role_name
JOIN module m ON m.name = p.module_name
WHERE NOT EXISTS (
  SELECT 1 FROM privilage pv WHERE pv.role_id = r.id AND pv.module_id = m.id);


-- ---------------------------------------------------------------------------
-- 11. Demo login accounts
--
--   *** EVERY ACCOUNT BELOW SHARES THE PASSWORD  DemoPass123  ***
--
-- They exist so the privilege matrix above can be seen working. They are the
-- one part of this file that is dangerous to leave in place: delete them
-- before the system is used for anything real.
--
--   DELETE FROM user_has_role WHERE user_id IN
--     (SELECT id FROM user WHERE username IN ('principal', 'teacher', 'clerk'));
--   DELETE FROM user WHERE username IN ('principal', 'teacher', 'clerk');
--
-- The Admin account is NOT created here. Bootstrap it with
-- POST /api/auth/createadmin and a password of your own choosing, as the
-- README describes.
-- ---------------------------------------------------------------------------

INSERT INTO user (username, password, useremail, status, added_datetime, employee_id, note)
SELECT u.username,
       '$2a$10$U1OwE/7XGklSLrNRK6GIwOVgsmFLchG037s.f8LHRaZwXDPMzEgce',
       u.email, TRUE, TIMESTAMP '2026-01-02 08:00:00',
       (SELECT e.id FROM employee e WHERE e.emp_no = u.emp_no),
       'Demo account created by sample-data.sql - delete before production use.'
FROM (
  SELECT 'principal' AS username, 'principal@scbc.lk' AS email, '00000001' AS emp_no, 'Principal' AS role_name
  UNION ALL SELECT 'teacher', 'teacher@scbc.lk', '00000002', 'Teacher'
  UNION ALL SELECT 'clerk',   'clerk@scbc.lk',   '00000097', 'Clerk'
) AS u
WHERE NOT EXISTS (SELECT 1 FROM user existing WHERE existing.username = u.username);

INSERT INTO user_has_role (user_id, role_id)
SELECT usr.id, r.id
FROM (
  SELECT 'principal' AS username, 'Principal' AS role_name
  UNION ALL SELECT 'teacher', 'Teacher'
  UNION ALL SELECT 'clerk',   'Clerk'
) AS u
JOIN user usr ON usr.username = u.username
JOIN role r ON r.name = u.role_name
WHERE NOT EXISTS (
  SELECT 1 FROM user_has_role uhr WHERE uhr.user_id = usr.id AND uhr.role_id = r.id);


-- ---------------------------------------------------------------------------
-- 12. Tidy up
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS seed_numbers;
DROP TABLE IF EXISTS seed_first_names;
DROP TABLE IF EXISTS seed_surnames;
DROP TABLE IF EXISTS seed_days;
DROP TABLE IF EXISTS seed_curriculum;
DROP TABLE IF EXISTS seed_privileges;
DROP TABLE IF EXISTS seed_grades;
DROP TABLE IF EXISTS seed_classes;
DROP TABLE IF EXISTS seed_roll;
DROP TABLE IF EXISTS seed_teachers;


-- ---------------------------------------------------------------------------
-- Removing the sample data
--
-- Delete in this order - each table below is referenced by the one above it,
-- so any other order trips a foreign key. This removes the demo records only;
-- the lookup rows from academic-seed.sql are left alone.
--
--   DELETE FROM student_has_attendence;
--   DELETE FROM attendence;
--   DELETE FROM payment;
--   DELETE FROM student_subject;
--   DELETE FROM student_registration;
--   DELETE FROM classroom_subject;
--   DELETE FROM grade_head;
--   UPDATE classroom SET employee_id = NULL;
--   DELETE FROM student;
--   DELETE FROM guardian;
--   DELETE FROM user_has_role WHERE user_id IN
--     (SELECT id FROM user WHERE username IN ('principal', 'teacher', 'clerk'));
--   DELETE FROM user WHERE username IN ('principal', 'teacher', 'clerk');
--   DELETE FROM privilage;
--   DELETE FROM employee;
-- ---------------------------------------------------------------------------
