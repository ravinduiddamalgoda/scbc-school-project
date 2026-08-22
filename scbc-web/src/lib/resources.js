import { api } from './api';

/**
 * Thin, typed-by-convention wrappers over the REST endpoints.
 *
 * Every module follows the same shape, so pages read the same way regardless
 * of which resource they manage.
 */
function crud(path) {
  return {
    list: () => api.get(path).then((r) => r.data),
    get: (id) => api.get(`${path}/${id}`).then((r) => r.data),
    create: (body) => api.post(path, body).then((r) => r.data),
    update: (id, body) => api.put(`${path}/${id}`, body).then((r) => r.data),
    remove: (id) => api.delete(`${path}/${id}`).then((r) => r.data),
  };
}

export const employees = {
  ...crud('/employees'),
  withoutAccount: () => api.get('/employees/without-account').then((r) => r.data),
};

export const students = crud('/students');
export const guardians = crud('/guardians');
export const users = crud('/users');
export const privileges = crud('/privileges');
export const subjects = crud('/subjects');
export const academicYears = crud('/academic-years');

/**
 * The bands subjects are grouped into on the mark sheet.
 *
 * These used to be a fixed array in the subject screen, which meant the mark
 * sheet's column groups could not be changed without a release.
 */
export const subjectCategories = crud('/subject-categories');

/**
 * Which subjects each grade is taught.
 *
 * The timetable editor reads this to pre-tick a class's subjects instead of
 * offering all twenty-nine, and both subject reports count against it.
 */
export const curriculum = {
  list: (gradeId) => api.get('/curriculum', { params: { gradeId } }).then((r) => r.data),
  saveForGrade: (gradeId, entries) =>
    api.put(`/curriculum/grades/${gradeId}`, entries).then((r) => r.data),
};

/**
 * Conduct, health, leadership, co-curricular activities and other talents.
 *
 * The record the leaving certificate's last four items are drafted from, so
 * they are kept as they happen rather than typed from memory at the counter.
 */
export const achievements = {
  list: (studentId, kind) =>
    api.get('/achievements', { params: { studentId, kind } }).then((r) => r.data),
  options: () => api.get('/achievements/options').then((r) => r.data),
  create: (body) => api.post('/achievements', body).then((r) => r.data),
  update: (id, body) => api.put(`/achievements/${id}`, body).then((r) => r.data),
  remove: (id) => api.delete(`/achievements/${id}`).then((r) => r.data),
};

/** What each grade is charged for a year. */
export const feeStructures = {
  list: (academicYearId) =>
    api.get('/fee-structures', { params: { academicYearId } }).then((r) => r.data),
  save: (academicYearId, rows) =>
    api.put('/fee-structures', rows, { params: { academicYearId } }).then((r) => r.data),
};

export const classes = {
  ...crud('/classes'),
  /**
   * Classes belong to one academic year; omitting it means "the current one".
   *
   * `mineOnly` narrows the list to classes the caller may actually change —
   * for a teacher, the one they are class teacher of. The attendance register
   * asks for it, because marking somebody else's class is refused on save and
   * offering it first is a poor way to explain the rule. Marks deliberately do
   * not: any teacher may enter marks for any class.
   *
   * Every row also carries `editable`, so a screen showing the whole list can
   * still tell which rows the caller owns.
   */
  list: (academicYearId, { mineOnly = false } = {}) =>
    api.get('/classes', { params: { academicYearId, mineOnly } }).then((r) => r.data),
  subjects: (id) => api.get(`/classes/${id}/subjects`).then((r) => r.data),
  /** Replaces the whole timetable: [{ subjectId, teacherId }]. */
  saveSubjects: (id, lines) => api.put(`/classes/${id}/subjects`, lines).then((r) => r.data),
  students: (id) => api.get(`/classes/${id}/students`).then((r) => r.data),

  /**
   * Brings timetables into line with the grade curriculum.
   *
   * A dry run by default: removing a subject takes its enrolments and any
   * marks with it, so the screen shows the cost and asks before applying it.
   */
  alignToCurriculum: ({ academicYearId, classroomId, dryRun = true, force = false } = {}) =>
    api
      .post('/classes/align-to-curriculum', null, {
        params: { academicYearId, classroomId, dryRun, force },
      })
      .then((r) => r.data),
};

export const enrolments = {
  ...crud('/enrolments'),
  list: (studentId) => api.get('/enrolments', { params: { studentId } }).then((r) => r.data),
};

export const terms = {
  ...crud('/terms'),
  list: (academicYearId) => api.get('/terms', { params: { academicYearId } }).then((r) => r.data),
  create: (body, academicYearId) =>
    api.post('/terms', body, { params: { academicYearId } }).then((r) => r.data),
  update: (id, body, academicYearId) =>
    api.put(`/terms/${id}`, body, { params: { academicYearId } }).then((r) => r.data),
};

/**
 * The days school is not conducted.
 *
 * Attendance reports count a day as conducted because a register exists for it,
 * so a holiday is what stops one being opened - and therefore what stops the
 * day reading as a whole-school absence.
 */
export const holidays = {
  list: (academicYearId) =>
    api.get('/holidays', { params: { academicYearId } }).then((r) => r.data),
  create: (body, academicYearId) =>
    api.post('/holidays', body, { params: { academicYearId } }).then((r) => r.data),
  update: (id, body, academicYearId) =>
    api.put(`/holidays/${id}`, body, { params: { academicYearId } }).then((r) => r.data),
  remove: (id) => api.delete(`/holidays/${id}`).then((r) => r.data),
};

export const gradeHeads = {
  list: (academicYearId) =>
    api.get('/grade-heads', { params: { academicYearId } }).then((r) => r.data),
  assign: (gradeId, employeeId, academicYearId) =>
    api
      .put(`/grade-heads/${gradeId}`, { employeeId }, { params: { academicYearId } })
      .then((r) => r.data),
  clear: (id) => api.delete(`/grade-heads/${id}`).then((r) => r.data),
};

export const payments = {
  ...crud('/payments'),
  list: (studentId) => api.get('/payments', { params: { studentId } }).then((r) => r.data),

  /**
   * Finds the student a receipt is for by admission number or name.
   *
   * Leading zeroes are optional - "3960" finds "00003960" - because that is
   * how the number appears on the paper file the clerk is reading from.
   */
  findStudents: (q) => api.get('/payments/students', { params: { q } }).then((r) => r.data),

  /** Fee for the year, total paid, balance, and every receipt. */
  feePosition: (studentId, academicYearId) =>
    api.get('/payments/fee-position', { params: { studentId, academicYearId } }).then((r) => r.data),

  /** The same statement as a printable page. */
  feePositionPdf: (studentId, academicYearId) =>
    downloadFile('/payments/fee-position/pdf', { studentId, academicYearId }),
};

/**
 * Attendance is addressed by (class, date) rather than by an id, because that
 * is what a register page is. The sheet comes back whether or not the day has
 * ever been saved, so the screen never has to create an empty one first.
 */
export const attendance = {
  sheet: (classroomId, date) =>
    api.get('/attendance', { params: { classroomId, date } }).then((r) => r.data),
  save: (classroomId, date, marks) =>
    api.put('/attendance', { classroomId, date, marks }).then((r) => r.data),
  markedDays: (classroomId, from, to) =>
    api.get('/attendance/days', { params: { classroomId, from, to } }).then((r) => r.data),
  remove: (id) => api.delete(`/attendance/${id}`).then((r) => r.data),

  /**
   * One student's attendance over a period, week by week.
   *
   * `availableLetters` on the response is what enables the letter buttons: the
   * server decides which of the three the record justifies, and re-checks the
   * same rule when one is asked for.
   */
  forStudent: (studentId, from, to) =>
    api.get(`/attendance/students/${studentId}`, { params: { from, to } }).then((r) => r.data),

  /** One of the three attendance letters, as a PDF. */
  letter: (studentId, type, from, to, meetingDate, meetingTime) =>
    downloadFile(`/attendance/students/${studentId}/letter`, {
      type,
      from,
      to,
      meetingDate: meetingDate || undefined,
      meetingTime: meetingTime || undefined,
    }),
};

/**
 * Subject-wise marks, addressed by (class, term) the way attendance is
 * addressed by (class, date).
 *
 * `save` returns the recalculated sheet rather than an acknowledgement, so the
 * totals, averages and ranks on screen after saving are the ones the server
 * computed - the page never does the arithmetic itself.
 */
export const marks = {
  sheet: (classroomId, termId) =>
    api.get('/marks/sheet', { params: { classroomId, termId } }).then((r) => r.data),
  save: (classroomId, termId, entries) =>
    api.put('/marks', { classroomId, termId, entries }).then((r) => r.data),
  excel: (classroomId, termId) =>
    downloadFile('/marks/sheet/excel', { classroomId, termId }),
  pdf: (classroomId, termId) => downloadFile('/marks/sheet/pdf', { classroomId, termId }),
};

/**
 * Leaving and character certificates.
 *
 * `draft` returns a filled-in but unsaved certificate; nothing is recorded
 * until it is issued. `pdf` renders a certificate that was already issued, from
 * the stored text - so a reprint is the document that was signed rather than
 * one rebuilt from a record that has changed since.
 */
export const certificates = {
  draft: (studentId, type) =>
    api.get('/certificates/draft', { params: { studentId, type } }).then((r) => r.data),
  list: (studentId) => api.get('/certificates', { params: { studentId } }).then((r) => r.data),
  issue: (body) => api.post('/certificates', body).then((r) => r.data),
  pdf: (id) => downloadFile(`/certificates/${id}/pdf`, {}),
  /** The register of everything issued, as a workbook. */
  register: (studentId) => downloadFile('/certificates/register/excel', { studentId }),

  /**
   * The reasons a leaving certificate may give.
   *
   * Served rather than hard-coded here so the list the form offers cannot
   * drift from the one the server records against.
   */
  leavingReasons: () => api.get('/certificates/leaving-reasons').then((r) => r.data),
};

/**
 * Uniform and book distribution, addressed by (class, kind) the way marks are
 * addressed by (class, term).
 */
export const distributions = {
  sheet: (classroomId, kind) =>
    api.get('/distributions/sheet', { params: { classroomId, kind } }).then((r) => r.data),
  save: (classroomId, kind, entries) =>
    api.put('/distributions', { classroomId, kind, entries }).then((r) => r.data),
  excel: (classroomId, kind) => downloadFile('/distributions/sheet/excel', { classroomId, kind }),
  items: () => api.get('/distributions/items').then((r) => r.data),
  createItem: (body) => api.post('/distributions/items', body).then((r) => r.data),
  updateItem: (id, body) => api.put(`/distributions/items/${id}`, body).then((r) => r.data),
  removeItem: (id) => api.delete(`/distributions/items/${id}`).then((r) => r.data),
};

/**
 * The Department of Examinations candidate workbooks.
 *
 * `check` is a dry run: it reports how many candidates there are and what is
 * missing, so the office fixes the records before submitting rather than after
 * the Department rejects the upload.
 */
export const examExports = {
  check: (exam, academicYearId) =>
    api.get('/exam-exports/check', { params: { exam, academicYearId } }).then((r) => r.data),
  download: (exam, academicYearId) => downloadFile('/exam-exports', { exam, academicYearId }),
};

/**
 * School Based Assessment: the Department's coursework marks.
 *
 * Marks are entered one grade and one term at a time; `sheet` is the merge of
 * all five columns, which is what the workbook prints.
 */
export const sba = {
  structure: () => api.get('/sba/structure').then((r) => r.data),
  sheet: (exam, examYear, subjectId, medium) =>
    api.get('/sba/sheet', { params: { exam, examYear, subjectId, medium } }).then((r) => r.data),
  save: (exam, examYear, subjectId, grade, term, entries, medium) =>
    api
      .put('/sba/marks', entries, {
        params: { exam, examYear, subjectId, grade, term, medium },
      })
      .then((r) => r.data),
  excel: (exam, examYear, subjectId, medium) =>
    downloadFile('/sba/sheet/excel', { exam, examYear, subjectId, medium }),
};

/**
 * What a parent may see: their own children, and nothing else.
 *
 * Every call is scoped server-side to the guardian on the signed-in account -
 * there is no student id to get wrong here, because one supplied by the client
 * is checked against that list rather than trusted.
 */
export const parentPortal = {
  children: () => api.get('/parent/children').then((r) => r.data),
  terms: (studentId) => api.get(`/parent/children/${studentId}/terms`).then((r) => r.data),
  marks: (studentId, termId) =>
    api.get(`/parent/children/${studentId}/marks`, { params: { termId } }).then((r) => r.data),
  attendance: (studentId, from, to) =>
    api.get(`/parent/children/${studentId}/attendance`, { params: { from, to } }).then((r) => r.data),
  payments: (studentId, academicYearId) =>
    api.get(`/parent/children/${studentId}/payments`, { params: { academicYearId } }).then((r) => r.data),
};

export const lookups = {
  designations: () => api.get('/lookups/designations').then((r) => r.data),
  grades: () => api.get('/lookups/grades').then((r) => r.data),
  statuses: () => api.get('/lookups/statuses').then((r) => r.data),
  studentStatuses: () => api.get('/lookups/student-statuses').then((r) => r.data),
  modules: () => api.get('/lookups/modules').then((r) => r.data),
  roles: () => api.get('/lookups/roles').then((r) => r.data),
  assignableRoles: () => api.get('/lookups/roles/assignable').then((r) => r.data),
  academicYears: () => api.get('/lookups/academic-years').then((r) => r.data),
  registrationStatuses: () => api.get('/lookups/registration-statuses').then((r) => r.data),
  subjects: () => api.get('/lookups/subjects').then((r) => r.data),
  paymentTypes: () => api.get('/lookups/payment-types').then((r) => r.data),
  mediums: () => api.get('/lookups/mediums').then((r) => r.data),
  appointmentTypes: () => api.get('/lookups/appointment-types').then((r) => r.data),
  educationQualifications: () => api.get('/lookups/education-qualifications').then((r) => r.data),
  guardians: () => api.get('/guardians').then((r) => r.data),
};

/**
 * Reports.
 *
 * `run` returns the report as data so the page can show it; `pdf` asks the
 * server for the same report already rendered. The PDF is produced from the
 * same query rather than from whatever happens to be on screen, so what is
 * filed is what was reported.
 */
export const reports = {
  catalogue: () => api.get('/reports').then((r) => r.data),
  /**
   * @param params - { academicYearId, classroomId, studentId, month }. Which
   *   of these a report needs is declared in the catalogue; unused ones are
   *   ignored by the server, so the caller never special-cases a report.
   */
  run: (key, params) => api.get(`/reports/${key}`, { params }).then((r) => r.data),
  pdf: (key, params) =>
    api
      .get(`/reports/${key}/pdf`, {
        params,
        responseType: 'blob',
        headers: { Accept: 'application/pdf' },
      })
      .then((response) => ({
        blob: response.data,
        filename: filenameFrom(response.headers['content-disposition']),
      })),
};

/**
 * Fetches a binary export and the filename the server chose for it.
 *
 * Shared by every download so the filename always comes from the server -
 * which is the only side that knows which class, term and year the file is
 * actually for.
 */
function downloadFile(path, params) {
  return api
    .get(path, { params, responseType: 'blob' })
    .then((response) => ({
      blob: response.data,
      filename: filenameFrom(response.headers['content-disposition']),
    }));
}

/** Pulls the server-chosen filename out of Content-Disposition. */
function filenameFrom(header) {
  if (!header) return null;
  // RFC 5987 form first (filename*=UTF-8''…), then the plain quoted form.
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}

export const auth = {
  me: () => api.get('/auth/me').then((r) => r.data),
  login: (username, password) =>
    api.post('/auth/login', { username, password }).then((r) => r.data),
  logout: () => api.post('/auth/logout'),
  updateProfile: (body) => api.put('/auth/profile', body).then((r) => r.data),
};
