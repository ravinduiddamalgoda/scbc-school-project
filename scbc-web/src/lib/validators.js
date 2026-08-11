/**
 * Field-level validation rules, carried over from the old validationFunction.js
 * so the browser catches the same mistakes before a request is sent. The server
 * enforces the same constraints independently.
 */

export const patterns = {
  // Letters, spaces, apostrophes and dots - covers Sri Lankan full names.
  personName: /^[A-Za-z][A-Za-z .'-]{1,99}$/,
  // Old 9-digit + V/X format, or the current 12-digit NIC.
  nic: /^([0-9]{9}[VvXx]|[0-9]{12})$/,
  // Local mobile/land numbers.
  phone: /^0[0-9]{9}$/,
  email: /^[^\s@]+@[^\s@]+\.[A-Za-z]{2,}$/,
  birthCertificate: /^[A-Za-z0-9/-]{6,12}$/,
};

export const required = (label) => (value) =>
  value === null || value === undefined || String(value).trim() === ''
    ? `${label} is required.`
    : null;

export const matches = (pattern, message) => (value) => {
  if (!value || String(value).trim() === '') return null;
  return pattern.test(String(value).trim()) ? null : message;
};

export const minLength = (length, label) => (value) => {
  if (!value) return null;
  return String(value).trim().length >= length
    ? null
    : `${label} must be at least ${length} characters.`;
};

export const maxLength = (length, label) => (value) => {
  if (!value) return null;
  return String(value).trim().length <= length
    ? null
    : `${label} must be at most ${length} characters.`;
};

/** Rejects a date in the future - used for dates of birth. */
export const notFuture = (label) => (value) => {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return `${label} is not a valid date.`;
  return date > new Date() ? `${label} cannot be in the future.` : null;
};

/** Rejects an implausible age range. */
export const ageBetween = (min, max, label) => (value) => {
  if (!value) return null;
  const birth = new Date(value);
  if (Number.isNaN(birth.getTime())) return null;

  const years = (Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000);
  if (years < min) return `${label} must be at least ${min} years old.`;
  if (years > max) return `${label} must be under ${max} years old.`;
  return null;
};

/**
 * Runs a schema of { field: [rule, ...] } against a values object and returns
 * a { field: message } map containing only the fields that failed.
 */
export function validate(values, schema) {
  const errors = {};

  for (const [field, rules] of Object.entries(schema)) {
    for (const rule of rules) {
      const message = rule(values[field], values);
      if (message) {
        errors[field] = message;
        break; // Report the first failure per field only.
      }
    }
  }

  return errors;
}
