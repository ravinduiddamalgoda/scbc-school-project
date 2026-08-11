import { useRef, useState } from 'react';
import { fileToDataUrl } from '@/lib/format';
import Avatar from './Avatar';
import Button from './Button';

const MAX_BYTES = 1024 * 1024; // 1 MB

/**
 * Photo upload with a live preview.
 *
 * Emits a data URL string, which is exactly what the API persists, so the value
 * can be dropped straight into the form state.
 */
export default function PhotoPicker({ value, onChange, name, label = 'Photo', error }) {
  const inputRef = useRef(null);
  const [localError, setLocalError] = useState(null);

  const handleFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setLocalError(null);

    if (!file.type.startsWith('image/')) {
      setLocalError('Choose an image file.');
      return;
    }

    // The photo is stored inline in the record, so an unbounded file would
    // bloat every list response that includes it.
    if (file.size > MAX_BYTES) {
      setLocalError('Images must be 1 MB or smaller.');
      return;
    }

    try {
      onChange(await fileToDataUrl(file));
    } catch {
      setLocalError('That image could not be read.');
    }
  };

  const clear = () => {
    onChange(null);
    setLocalError(null);
    if (inputRef.current) inputRef.current.value = '';
  };

  const message = error ?? localError;

  return (
    <div>
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        {label}
      </span>

      <div className="flex items-center gap-4 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900">
        <Avatar src={value} name={name} size="lg" />

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="secondary" onClick={() => inputRef.current?.click()}>
              {value ? 'Replace' : 'Upload'}
            </Button>
            {value && (
              <Button size="sm" variant="ghost" onClick={clear}>
                Remove
              </Button>
            )}
          </div>

          <p className="mt-1.5 text-xs text-slate-400 dark:text-slate-500">
            JPG or PNG, up to 1 MB.
          </p>
        </div>

        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          onChange={handleFile}
          className="sr-only"
          aria-label={`Upload ${label.toLowerCase()}`}
        />
      </div>

      {message && (
        <p className="mt-1.5 text-xs font-medium text-negative-600 dark:text-negative-500">
          {message}
        </p>
      )}
    </div>
  );
}
