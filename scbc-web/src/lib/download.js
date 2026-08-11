/**
 * Saves a Blob the server sent as a file.
 *
 * The link is created, clicked and removed synchronously, and the object URL is
 * revoked on the next tick - holding on to it would keep the whole PDF in
 * memory for the lifetime of the tab.
 */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.rel = 'noopener';

  document.body.appendChild(link);
  link.click();
  link.remove();

  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

/**
 * Turns a title into a safe filename stem, used when the server did not send
 * one in Content-Disposition.
 */
export function toFileStem(title) {
  return String(title ?? 'report')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}
