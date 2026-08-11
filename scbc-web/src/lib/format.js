/** Presentation helpers shared across tables and detail panels. */

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const DATETIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : DATE_FORMAT.format(date);
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : DATETIME_FORMAT.format(date);
}

/** Blank-safe cell value. */
export function orDash(value) {
  if (value === null || value === undefined) return '—';
  const text = String(value).trim();
  return text === '' ? '—' : text;
}

/** Whole years between a date of birth and today. */
export function ageFrom(dob) {
  if (!dob) return null;
  const birth = new Date(dob);
  if (Number.isNaN(birth.getTime())) return null;

  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const monthDelta = today.getMonth() - birth.getMonth();
  if (monthDelta < 0 || (monthDelta === 0 && today.getDate() < birth.getDate())) {
    age -= 1;
  }
  return age;
}

/** Initials for the avatar fallback. */
export function initials(name) {
  if (!name) return '?';
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

/**
 * Reads a File into a data URL.
 *
 * The API stores photos as the UTF-8 bytes of exactly this string and returns
 * them the same way, so the value round-trips straight into an <img src>.
 */
export function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('The image could not be read.'));
    reader.readAsDataURL(file);
  });
}

/** Deterministic ISO date string (yyyy-MM-dd) for <input type="date">. */
export function toDateInput(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString().slice(0, 10);
}
