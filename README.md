# SCBC — Sri Chandananda Buddhist College Management System

Client–server rewrite of the original Spring Boot + Thymeleaf monolith.

| Tier | Folder | Stack | Port |
| --- | --- | --- | --- |
| API | [`scbck/`](scbck/) | Spring Boot 3.5, Java 21, Spring Security, JPA, MySQL | 8080 |
| Client | [`scbc-web/`](scbc-web/) | React 19, Vite 6, Tailwind CSS 4, React Router 7, axios | 5173 |

The API serves JSON only — no templates, no redirects. The React client owns
all rendering and navigation.

---

## 1. Prerequisites

- JDK 21
- Node.js 20 or newer
- MySQL 8 with a database named `scbc`

## 2. Configure the database

`application.properties` is not in the repository — it holds the datasource
credentials and the Admin bootstrap password. Copy the template on a fresh
clone:

```bash
cp scbck/src/main/resources/application.properties.example \
   scbck/src/main/resources/application.properties
```

Every value in it reads `${ENV_VAR:default}`, so the usual practice is to leave
the file alone and export the variables instead:

```bash
# PowerShell
$env:SCBC_DB_URL      = "jdbc:mysql://localhost:3306/scbc"
$env:SCBC_DB_USERNAME = "root"
$env:SCBC_DB_PASSWORD = "your-password"
```

`spring.jpa.hibernate.ddl-auto=update` is set, so on first start Hibernate adds
what the ER model does not yet contain:

- the `guardian` table,
- `student.guardian_id`,
- the audit columns on `student` (`added_datetime`, `updated_datetime`,
  `deleted_datetime`, and the matching `*_user_id` columns), which `employee`
  already had,
- the two link tables the reports need, `classroom_subject` and
  `student_subject`, plus the extra columns on `subject_detail` and
  `academic_year` — see [Reporting](#reporting) for why.

The rest of the schema still comes from [`scbcer.mwb`](scbcer.mwb) —
forward-engineer it from MySQL Workbench before the first run.

### Seed data

Start the API once first — Hibernate creates the tables on boot — then stop it
and load the reference data:

```bash
mysql -u root -p scbc < scbck/seed/academic-seed.sql
```

If MySQL runs in Docker, pipe the file into the container instead:

```bash
docker exec -i <container> mysql -u <user> -p<password> scbc < scbck/seed/academic-seed.sql
```

> **Already have duplicated lookup rows?** Two "Active" statuses, two
> "Teacher" designations, two "Grade 1"s — that comes from pasting the old
> README seed block twice. Fix it before seeding, or the scripts stop halfway
> on *Subquery returns more than 1 row*:
>
> ```bash
> mysql -u root -p scbc < scbck/seed/deduplicate-lookups.sql
> ```
>
> It repoints every reference to the lowest-numbered row of each name and then
> deletes the rest, so nothing that points at a lookup is lost. Back up first.

That is everything the application needs to run: the four roles, the employee
and student statuses, the three designations, all ten privilege modules, the
`registration_status` and `payment_type` rows, grades 1–13, the 29 curriculum
subjects, an academic year with three terms, and one class per grade (A–G, plus
the four A/L streams for grades 12 and 13). Status id `3` is "Deleted" for both
employees and students.

Class teachers, grade heads, timetables, enrolments and attendance are left
empty — those are filled in through the UI, and each report says what is
missing rather than printing a zero.

#### Sample data

To see the nine reports populated instead of empty, follow it with:

```bash
mysql -u root -p scbc < scbck/seed/sample-data.sql
```

It builds a whole plausible school for 2026 — 100 staff, 2,808 students across
the 85 classes (28 to 38 each, as the source workbooks show), guardians, class
teachers, grade heads, 876 timetable lines, 24,024 subject enrolments, three
weeks of marked attendance for every class, two fee receipts per student, and
the role privilege matrix.

> **This is demo data.** Do not run it against a database holding real records.
> It also creates three login accounts — `principal`, `teacher` and `clerk` —
> that **all share the password `DemoPass123`**, so the privilege matrix can be
> seen working. Delete them before the system is used for anything real; the
> script ends with the exact statements to do so, along with the full teardown
> order for the rest of the data.

Both scripts are re-runnable: a second run inserts nothing. Neither creates the
Admin account — bootstrap that with `POST /api/auth/createadmin` and a password
of your own, as above.

## 3. Run the API

```bash
cd scbck
./gradlew bootRun
```

### Create the first Admin account

Neither seed script creates an Admin row, and no password is hard-coded. Set
one **in the same terminal that starts the API**, and it is created on boot:

```bash
# Git Bash / Linux / macOS
export SCBC_ADMIN_INITIAL_PASSWORD="ChangeMeNow123"
./gradlew bootRun
```

```powershell
# PowerShell
$env:SCBC_ADMIN_INITIAL_PASSWORD = "ChangeMeNow123"
./gradlew bootRun
```

The log line to look for is:

```
AdminBootstrap : Admin bootstrap: created the Admin account.
```

That writes `Admin` / `adminscbc@gmail.com` with the password BCrypt-hashed and
the `user_has_role` row linking it to the Admin role. Sign in as `Admin`,
change the password from **My profile**, then unset the variable and restart.

Two prerequisites, each of which the log names explicitly if unmet:

1. `scbck/seed/academic-seed.sql` has been loaded, so the `role` table holds an
   `Admin` row — *"no 'Admin' row in the role table"* means it has not.
2. The variable is set **before** start-up. It is read once as the context
   builds, so exporting it in a second terminal while the API is already
   running does nothing — *"no initial password configured"* means it was not
   visible to that process.

Neither aborts the boot; the API comes up either way, you fix the cause and
restart.

<details>
<summary>Calling <code>POST /api/auth/createadmin</code> by hand</summary>

The start-up path above covers the normal case. The endpoint still exists for
recreating the account without a restart, but a bare `curl -X POST` is answered
with:

```json
{"status":403,"error":"Forbidden","message":"You do not have permission to perform this action."}
```

That is CSRF, not authorisation: the endpoint is reachable while logged out,
but the double-submit token is still required. Fetch one first and echo it
back.

```bash
curl -c cookies.txt http://localhost:8080/api/auth/csrf
curl -b cookies.txt -X POST http://localhost:8080/api/auth/createadmin \
     -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN cookies.txt | cut -f7)"
```

```powershell
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest http://localhost:8080/api/auth/csrf -WebSession $s | Out-Null
$token = $s.Cookies.GetCookies("http://localhost:8080")["XSRF-TOKEN"].Value
Invoke-RestMethod -Method Post http://localhost:8080/api/auth/createadmin `
    -WebSession $s -Headers @{ "X-XSRF-TOKEN" = $token }
```

It returns 409 once the account exists, and 400 if no password was configured
at start-up.

</details>

## 4. Run the client

```bash
cd scbc-web
npm install
npm run dev          # http://localhost:5173
```

Vite proxies `/api` to `http://localhost:8080`, so the browser stays on one
origin and the session cookie is first-party.

The dev server is pinned to port 5173 (`strictPort`). That is deliberate: the
proxy forwards the browser's `Origin` header unchanged, so the API's CORS rules
still apply to proxied calls, and only 5173 and 4173 are allowed by default.

> **Login returns 403 with an empty body?** A second `npm run dev` used to
> start silently on 5174, a third on 5175, and the API refused them —
> `Invalid CORS request`, no content-type, nothing to suggest the port was at
> fault. `strictPort` now makes the port conflict the error instead. If you do
> need another port, widen the allowlist rather than editing code:
>
> ```bash
> $env:SCBC_CORS_ORIGINS = "http://localhost:[*]"   # any localhost port
> ```
>
> Entries are patterns; an entry without a wildcard still matches exactly.

---

## Architecture

### Authentication

Session-based, in an **HttpOnly** cookie. No token is ever written to
`localStorage`, so an XSS bug cannot exfiltrate credentials.

```
POST /api/auth/login   → 200 + user + privilege matrix, sets JSESSIONID
GET  /api/auth/me      → 200 restores the session on refresh, or 401
POST /api/auth/logout  → 204
GET  /api/auth/csrf    → 204, primes the XSRF-TOKEN cookie
```

**CSRF is enabled** (it was disabled before). The server issues an
`XSRF-TOKEN` cookie; axios echoes it in `X-XSRF-TOKEN` automatically.

To use JWT instead, replace `AuthController.login` and swap
`SessionCreationPolicy.IF_REQUIRED` for `STATELESS` in
[`SecurityConfig`](scbck/src/main/java/com/scbck/config/SecurityConfig.java) —
nothing else in either tier depends on the choice.

### Authorisation

Every module route is gated twice:

1. **Client** — `RequirePrivilege` hides menu entries, buttons and routes.
2. **Server** — `PrivilegeService.requireX()` re-checks on every request and
   throws a 403. This is the boundary that matters; the client-side check is
   only there to avoid offering actions that would fail.

`GET /api/auth/me` returns the whole privilege matrix in one payload, replacing
the old `/modulewithoutuser` call that hid elements by CSS class after render.

### API surface

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/employees[/{id}]` | Employee CRUD |
| GET | `/api/employees/without-account` | Staff with no login |
| GET/POST/PUT/DELETE | `/api/students[/{id}]` | Student CRUD |
| GET/POST/PUT/DELETE | `/api/guardians[/{id}]` | Guardian CRUD |
| GET/POST/PUT/DELETE | `/api/users[/{id}]` | Account management |
| GET/POST/PUT/DELETE | `/api/privileges[/{id}]` | Permission matrix |
| GET/POST/PUT/DELETE | `/api/subjects[/{id}]` | Curriculum subjects |
| GET/POST/PUT/DELETE | `/api/academic-years[/{id}]` | Academic years |
| GET/POST/PUT/DELETE | `/api/classes[/{id}]` | Classes, scoped by `?academicYearId=` |
| GET/PUT | `/api/classes/{id}/subjects` | A class's timetable and its subject teachers |
| GET | `/api/classes/{id}/students` | A class's roll |
| GET/POST/PUT/DELETE | `/api/enrolments[/{id}]` | Student → class placement and subject choices |
| GET/PUT | `/api/attendance?classroomId=&date=` | A class's register for one day |
| GET | `/api/attendance/days?classroomId=&from=&to=` | Which days already have a register |
| DELETE | `/api/attendance/{id}` | Remove a day — it becomes "school not conducted" |
| GET/POST/PUT/DELETE | `/api/terms[/{id}]` | Terms within a year |
| GET/PUT/DELETE | `/api/grade-heads[/{gradeId}]` | Which teacher heads each grade |
| GET/POST/PUT/DELETE | `/api/payments[/{id}]` | Fee receipts |
| GET | `/api/reports` | The catalogue: keys, titles and the parameters each needs |
| GET | `/api/reports/{key}` | One report as data, with `?academicYearId=&classroomId=&studentId=&month=` |
| GET | `/api/reports/{key}/pdf` | The same report as a PDF download |
| GET | `/api/lookups/{designations,grades,statuses,student-statuses,modules,roles,academic-years,registration-statuses,subjects}` | Dropdown data |

Errors are uniform:

```json
{ "timestamp": "…", "status": 409, "error": "Conflict",
  "message": "The NIC 200012345678 already belongs to another employee.",
  "path": "/api/employees" }
```

The old protocol returned `200 OK` with the body `"Save not completed: …"`, so
the client had to string-match to tell success from failure.

---

## Reporting

Nine reports were kept as spreadsheets under
[`Report Exports/`](Report%20Exports/) and retyped by hand whenever a student
moved class or a register was marked. They are now generated from the records
and exported as PDF:

| Report | What it answers | Needs |
| --- | --- | --- |
| Class Teachers | Which teacher is responsible for each class | year |
| Student Count of Classes | How many students are on each class's roll | year |
| Subject Wise Teachers | How many teachers take each subject, per grade | year |
| Subject wise Student Count of Classes | How many students in each class take each subject | year |
| Medium wise Student Count of Classes | How many students sit in each medium | year |
| Grade Heads | Which teacher heads each grade | year |
| Attendance Register | One class's daily register for a month, with weekly totals | year, class, month |
| Week Attendance Summary | Days conducted, days attended and the percentage, per term | year, class |
| Fees Details | Every fee payment recorded against one student | student |

### What had to be built first

None of them could be produced against the schema as it stood. The ER model
defines `classroom`, `subject_detail` and `attendence` but the application
never mapped any of them, and four relationships were missing from the model
altogether:

| Added | Why the report needed it |
| --- | --- |
| `classroom`, `academic_year` (mapped) | A student carried only a grade. A grade holds seven classes, so "how many are in Grade 6 B" had no answer. |
| `student_registration` (mapped) | The row that places a student in a class for a year. |
| `subject_detail` (mapped, plus `code`, `category`, `active`) | Nothing wrote to the subject table, so both subject reports had no source data. |
| `classroom_subject` (**new**) | Nothing recorded that a given teacher takes a given subject for a given class. |
| `student_subject` (**new**) | Optional subjects are taken by a subset of a class. Without this, a "subject count" could only repeat the class size. |
| `attendence`, `student_has_attendence` (mapped) | Attendance could not be marked at all, so both attendance reports had nothing to count. |
| `term` (**new**) | The Week Attendance workbook had three column groups labelled First/Second/Third Term and nothing recorded which dates those were. |
| `grade_head` (**new**) | The Grade Heads workbook had a name column nothing could fill in — a grade head is not a class teacher. |
| `classroom.medium` (**new**) | The Medium wise workbook splits the roll by language of instruction, which is a property of the class. |
| `payment` (mapped) | The fee history needs the grade the money was for, which is the enrolment it settled. |

`student_subject` points at `classroom_subject` rather than at the subject, so
the database cannot hold "Grade 6 B takes Combined Maths" — a student can only
be enrolled in a subject their own class is actually taught.

### Attendance marking

The unit of work is a register page, not a mark: pick a class and a day, go
down the roll, save once. `GET /api/attendance?classroomId=&date=` returns that
page whether or not it has ever been saved, so the screen looks the same before
the first mark as after — no empty register is created that a failed save could
leave behind. Saving again on the same date corrects the day rather than adding
a second register, which the unique constraint on `(classroom, date)` enforces
even under concurrent saves.

Three rules the marking screen and the reports agree on:

- **The existence of a register means school was conducted.** Both attendance
  reports count days that way, so a holiday needs no calendar entry — it is
  simply a date nobody marked. Deleting a day's register makes it count as no
  school held.
- **Absences are stored, not implied.** A student with no mark is *unmarked*,
  which is a different fact from absent; the register prints a blank for them,
  not a zero. Conflating the two turns a half-finished register into a day of
  perfect attendance and the percentage never recovers.
- **The roll comes from the enrolments.** A student admitted today appears on
  tomorrow's register with nothing else to do, and the same
  active-and-not-deleted filter the count reports use applies here, so a roll
  and a head count can never disagree.

### How a report is built

Every report reduces to the same shape — titled sections of headed rows — so
one PDF writer renders them and one React component displays them. Each also
declares the parameters it needs (year, class, student, month), and the client
renders exactly those controls from the catalogue.

Adding a tenth report is a method on one of the three builders
([`ClassReports`](scbck/src/main/java/com/scbck/service/ClassReports.java),
[`AttendanceReports`](scbck/src/main/java/com/scbck/service/AttendanceReports.java),
[`FeeReports`](scbck/src/main/java/com/scbck/service/FeeReports.java)) plus one
line in the catalogue in
[`ReportService`](scbck/src/main/java/com/scbck/service/ReportService.java). It
then appears in the client's menu, with the right inputs, without a line
changing in the client.

Sections are the grade bands the original workbooks laid out side by side
(1–5, 6–9, 10–11, 12–13). That is not decoration: a band carries only the
subjects its own grades are taught, which is what keeps a thirty-column
subject matrix printable at a readable size.

Two rules the reports enforce, which a hand-kept spreadsheet could not:

- A soft-deleted student, or a cancelled enrolment, stops counting immediately
  — the same filter is used by the class head count, the subject counts, the
  attendance roll and the medium split, so none of them can disagree.
- A teacher taking one subject across seven classes counts **once** per grade
  in Subject Wise Teachers, because the staffing decision turns on distinct
  people, not on timetable lines.
- A percentage over zero days conducted prints as `—`, not `0%`. The source
  workbook showed the same case as `#DIV/0!`.
- The Fees Details grade comes from the enrolment the payment settled, not from
  the student's current grade — otherwise promoting a student would relabel
  every receipt they had ever paid.

### Export

`GET /api/reports/{key}/pdf` returns the report as `application/pdf` with the
filename set in `Content-Disposition`. It is rendered server-side with
[OpenPDF](https://github.com/LibrePDF/OpenPDF), from the same
`ReportService` call that produced the JSON on screen — so the printout and
the screen cannot drift apart, the same privilege check applies to both, and
the client ships no PDF library. Wide reports are laid out landscape, and long
ones repeat their column headings on every page.

---

## What changed beyond the port

Fixing these was unavoidable — the affected screens could not be ported working.

**Correctness**

- Student create/update/delete **did not exist**. The browser posted new
  students to `/employee/insert`, writing student records into the employee
  table. Implemented properly against `student`.
- `StudentDao.getNextStuNo()` generated admission numbers from
  `MAX(employee.emp_no)`. Now reads the student table.
- `/student/alldata` checked the **Employee** privilege. Now checks Student.
- The Guardian screen had no entity, repository or controller at all. Added,
  matching the `guardian` table in the ER model.
- `Student` was missing the `guardian_id` foreign key the ER model defines.
- Employee update threw a `NullPointerException` whenever the NIC changed —
  the email and mobile duplicate checks dereferenced the NIC lookup result.
- The same checks compared `Integer` ids with `!=`, which silently breaks past
  id 127.
- `MyUserServiceDetail` NPE'd on an unknown username instead of failing
  authentication.
- Privilege parsing NPE'd for a user with no privilege rows; now returns a
  clean 403.

**Security**

- Password hashes were serialised in `/user/alldata`. `password` is now
  write-only and an [integration test](scbck/src/test/java/com/scbck/AuthApiIntegrationTest.java)
  asserts no response ever contains one.
- CSRF protection re-enabled.
- `/createadmin` no longer hard-codes the password `12345` and is idempotent.
- Database credentials moved to environment variables.
- User update stored the request body's password verbatim; it is now hashed,
  and blank means "keep the current password".

**Portability**

- Four native queries hard-coded the schema name — `FROM scbc.privilage`,
  `FROM scbc.module`, `FROM scbc.user_has_role`, `FROM scbc.employee`. Pointed
  at a database under any other name (a staging copy, a restore, a second
  school) they silently read the wrong schema: every user came back with an
  empty privilege matrix and appeared to have no access at all, and staff
  numbers were generated from a table the application could not see. They now
  use the connection's own database.

**Structure**

- Six near-identical lookup controllers merged into `LookupController`.
- `UserPrivilageController` (a `@Controller` used as a helper) became
  `PrivilegeService`.
- Request/response DTOs where entities leaked internals.
- Photos are exchanged as plain data-URL strings; the old client had to run
  `atob()` on a double-encoded value.

**Known gaps** — flagged, not fixed:

- No pagination on the server. Lists are fetched whole and paged in the
  browser; fine at current volume, not at a full roster.
- Photos are stored inline as `LONGBLOB` and travel in every list response.
  A dedicated `/photo/{id}` endpoint is the next step.
- Still no Flyway/Liquibase. `ddl-auto=update` is a stop-gap; switch to
  `validate` plus migrations before production.
- Exams and results remain unimplemented. The dashboard marks them **Planned**.
- Payments record money received; they are **not a billing engine**. "Amount
  due" is entered with the receipt rather than derived from a fee schedule, so
  the `payment_category` and `pay_type` tables in the ER model are still
  unmapped. That is all the Fees Details report needs, but it is not invoicing.
- The seed scripts are exercised by the test suite against H2 in MySQL mode,
  which proves their logic but not the dialect. They have also been run by hand
  against MySQL 8.4 — read them before running them on a populated database.
- `sample-data.sql` creates three shared-password demo logins. They are clearly
  marked and the script carries the statements to delete them, but they are the
  one thing in this repository that is genuinely unsafe to leave in place.
- The attendance tables keep the ER model's spelling — `attendence` and
  `student_has_attendence` — so a schema forward-engineered from `scbcer.mwb`
  still matches. The Java classes spell it correctly.

---

## Tests

```bash
cd scbck && ./gradlew test     # 38 tests, H2 in-memory — no MySQL needed
cd scbc-web && npm run build
```

The auth tests cover anonymous rejection, login, session reuse, CSRF
enforcement, logout, password-change rules, the Admin bootstrap guard, and the
no-hash-in-responses guarantee.

The report tests run the whole pipeline against deliberately uneven fixtures —
a teacher shared between two classes, an optional subject only some students
take, one soft-deleted student, one cancelled enrolment, a week where one
student is often out and another is left unmarked — so a report that merely
echoed a class size, or that read "not marked" as "absent", would fail. They
also assert that every report renders to a real PDF, that enrolling a student
moves the reported figure on the next request, and that saving the same
register twice corrects the day rather than duplicating it.

`SeedScriptIntegrationTest` runs both seed scripts twice against the schema
Hibernate generates from the entities, so they cannot drift from a renamed
column or quietly stop being re-runnable. It also asserts the shape of what the
sample data produces — every class has a teacher, both media are represented,
the stored day totals agree with the marks beneath them, optional subjects are
taken by a subset rather than by everyone — because a seed that silently builds
a uniform school makes every report look correct and prove nothing.

## The previous UI

The Thymeleaf templates and their jQuery/Bootstrap assets are preserved under
[`scbck/legacy-thymeleaf-ui/`](scbck/legacy-thymeleaf-ui/). They are outside
`src/main/resources`, so Spring no longer serves them, and the Thymeleaf
dependencies have been removed from the build. Delete the folder once you are
satisfied with the replacement.

## Production build

```bash
cd scbc-web
npm run build        # → dist/
```

Serve `dist/` from any static host. Point the client at the API and allow its
origin on the server:

```bash
VITE_API_BASE_URL=https://api.example.org/api      # client build
SCBC_CORS_ORIGINS=https://scbc.example.org         # API
```

Also enable `server.servlet.session.cookie.secure=true` once the API is behind
HTTPS.
