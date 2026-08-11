import { useCallback, useRef, useState } from 'react';
import { validate } from '@/lib/validators';

/**
 * Minimal form state helper: values, per-field errors and touched tracking.
 *
 * A field is validated on blur and then live on every keystroke, so an error
 * appears once the user has finished with a field but corrections show
 * immediately.
 */
export function useForm(initialValues, schema = {}) {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});

  // Mirrors of the latest state, so callbacks can read current values without
  // nesting one setState updater inside another - updaters must stay pure.
  const valuesRef = useRef(values);
  const touchedRef = useRef(touched);

  const commitValues = useCallback((next) => {
    valuesRef.current = next;
    setValues(next);
  }, []);

  const commitTouched = useCallback((next) => {
    touchedRef.current = next;
    setTouched(next);
  }, []);

  /** Recomputes the error for one field against a given value set. */
  const revalidateField = useCallback(
    (field, sourceValues) => {
      if (!schema[field]) return;

      const fieldErrors = validate(sourceValues, { [field]: schema[field] });

      setErrors((current) => {
        const { [field]: _removed, ...rest } = current;
        return fieldErrors[field] ? { ...rest, [field]: fieldErrors[field] } : rest;
      });
    },
    [schema],
  );

  const setValue = useCallback(
    (field, value) => {
      const next = { ...valuesRef.current, [field]: value };
      commitValues(next);

      // Only re-validate a field the user has already visited, so errors do
      // not appear while they are still typing into it for the first time.
      if (touchedRef.current[field]) {
        revalidateField(field, next);
      }
    },
    [commitValues, revalidateField],
  );

  /** Convenience handler for native inputs. */
  const handleChange = useCallback(
    (field) => (event) => {
      const target = event.target;
      const value = target.type === 'checkbox' ? target.checked : target.value;
      setValue(field, value);
    },
    [setValue],
  );

  const handleBlur = useCallback(
    (field) => () => {
      commitTouched({ ...touchedRef.current, [field]: true });
      revalidateField(field, valuesRef.current);
    },
    [commitTouched, revalidateField],
  );

  /** Validates everything and marks every field touched. Returns true if valid. */
  const validateAll = useCallback(() => {
    const allErrors = validate(valuesRef.current, schema);
    setErrors(allErrors);
    commitTouched(Object.fromEntries(Object.keys(schema).map((field) => [field, true])));
    return Object.keys(allErrors).length === 0;
  }, [schema, commitTouched]);

  const reset = useCallback(
    (nextValues = initialValues) => {
      commitValues(nextValues);
      commitTouched({});
      setErrors({});
    },
    [initialValues, commitValues, commitTouched],
  );

  /** Applies a server-side field error, e.g. after a 409 conflict. */
  const setFieldError = useCallback(
    (field, message) => {
      setErrors((current) => ({ ...current, [field]: message }));
      commitTouched({ ...touchedRef.current, [field]: true });
    },
    [commitTouched],
  );

  return {
    values,
    errors,
    touched,
    setValue,
    setValues: commitValues,
    handleChange,
    handleBlur,
    validateAll,
    reset,
    setFieldError,
    /** Props to spread onto a controlled text-like input. */
    field: (name) => ({
      value: values[name] ?? '',
      onChange: handleChange(name),
      onBlur: handleBlur(name),
      error: touched[name] ? errors[name] : undefined,
    }),
  };
}
