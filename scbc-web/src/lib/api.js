import axios from 'axios';

/**
 * Single axios instance for the whole client.
 *
 * withCredentials sends the JSESSIONID session cookie; axios also reads the
 * XSRF-TOKEN cookie the server sets and echoes it back in the X-XSRF-TOKEN
 * header, which satisfies Spring Security's CSRF filter.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { Accept: 'application/json' },
});

/**
 * An API failure with the server's message already extracted.
 */
export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
  }

  get isUnauthorized() {
    return this.status === 401;
  }

  get isForbidden() {
    return this.status === 403;
  }
}

/** Callbacks fired when the session turns out to be gone. */
const sessionExpiredHandlers = new Set();

export function onSessionExpired(handler) {
  sessionExpiredHandlers.add(handler);
  return () => sessionExpiredHandlers.delete(handler);
}

/**
 * Normalises every failure into an ApiError carrying a human-readable message,
 * so callers never have to inspect axios internals or guess at response shapes.
 */
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!error.response) {
      return Promise.reject(
        new ApiError('Cannot reach the server. Check that the API is running.', 0, null),
      );
    }

    const { status, config } = error.response;

    // A failed download arrives as a Blob because the request asked for one.
    // Without this the JSON error inside it is invisible and every failed
    // export would report the same generic sentence.
    const data = await readErrorBody(error.response);

    const message =
      data?.message ||
      data?.error ||
      (status === 401
        ? 'Your session has ended. Please sign in again.'
        : 'The request could not be completed.');

    // A 401 anywhere except the login call itself means the session lapsed.
    const isLoginAttempt = config?.url?.includes('/auth/login');
    if (status === 401 && !isLoginAttempt) {
      sessionExpiredHandlers.forEach((handler) => handler());
    }

    return Promise.reject(new ApiError(message, status, data));
  },
);

/** Unwraps the error body, reading it out of a Blob when the call asked for one. */
async function readErrorBody({ data }) {
  if (!(data instanceof Blob)) return data;

  try {
    return JSON.parse(await data.text());
  } catch {
    // Not JSON - an HTML error page, or an empty body.
    return null;
  }
}

/**
 * Primes the XSRF-TOKEN cookie. Called once on start-up so the very first
 * mutating request (the login POST) already has a token to send.
 */
export async function primeCsrfToken() {
  try {
    await api.get('/auth/csrf');
  } catch {
    // Not fatal: the server may be down, which the caller surfaces separately.
  }
}
