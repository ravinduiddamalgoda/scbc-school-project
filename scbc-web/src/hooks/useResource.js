import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/context/ToastContext';

/**
 * Loads a collection once and exposes it with a reload handle.
 *
 * The old client used synchronous jQuery AJAX, which froze the tab on every
 * fetch and stored the error string in the same variable as the data. This
 * keeps data and error strictly separate and never blocks the UI thread.
 *
 * @param loader     - () => Promise<T[]>, must be stable (wrap in useCallback)
 * @param options.enabled - skip the fetch when false, e.g. without privilege
 */
export function useResource(loader, { enabled = true, onError } = {}) {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(null);

  // Guards against a state update after the component has gone away.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const reload = useCallback(async () => {
    if (!enabled) {
      setData([]);
      setLoading(false);
      return [];
    }

    setLoading(true);
    setError(null);

    try {
      const result = await loader();
      if (mounted.current) setData(Array.isArray(result) ? result : []);
      return result;
    } catch (caught) {
      if (mounted.current) {
        setError(caught);
        setData([]);
      }
      onError?.(caught);
      return [];
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, [loader, enabled, onError]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, loading, error, reload, setData };
}

/**
 * Wraps a mutating call with busy state, toast feedback and a refresh.
 *
 * Every create/update/delete in the app goes through this, so success and
 * failure are reported the same way everywhere.
 */
export function useMutation({ onSuccess } = {}) {
  const [saving, setSaving] = useState(false);
  const toast = useToast();

  const run = useCallback(
    async (action, { successMessage } = {}) => {
      setSaving(true);
      try {
        const result = await action();
        if (successMessage) toast.success(successMessage);
        await onSuccess?.(result);
        return { ok: true, result };
      } catch (error) {
        // The interceptor has already reduced this to a readable sentence.
        toast.error(error.message);
        return { ok: false, error };
      } finally {
        setSaving(false);
      }
    },
    [toast, onSuccess],
  );

  return { run, saving };
}
