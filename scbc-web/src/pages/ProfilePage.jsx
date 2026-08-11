import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/context/ToastContext';
import { useForm } from '@/hooks/useForm';
import { auth as authApi } from '@/lib/resources';
import { matches, minLength, patterns, required } from '@/lib/validators';

import PageHeader, { FormSection } from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Badge, { BoolMark } from '@/components/ui/Badge';
import PhotoPicker from '@/components/ui/PhotoPicker';
import { TextField } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const SCHEMA = {
  username: [required('Username'), minLength(3, 'Username')],
  email: [required('Email'), matches(patterns.email, 'Enter a valid email address.')],
  newPassword: [minLength(5, 'New password')],
  oldPassword: [
    (value, values) =>
      values.newPassword && !value ? 'Enter your current password to set a new one.' : null,
  ],
  confirmPassword: [
    (value, values) => {
      if (!values.newPassword) return null;
      if (!value) return 'Re-enter the new password.';
      return value === values.newPassword ? null : 'The passwords do not match.';
    },
  ],
};

export default function ProfilePage() {
  const { user, refresh, logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [saving, setSaving] = useState(false);

  const form = useForm(
    {
      username: user?.username ?? '',
      email: user?.email ?? '',
      photo: user?.photo ?? null,
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
    },
    SCHEMA,
  );

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    setSaving(true);
    try {
      const usernameChanged = form.values.username.trim() !== user.username;
      const passwordChanged = !!form.values.newPassword;

      const response = await authApi.updateProfile({
        username: form.values.username.trim(),
        email: form.values.email.trim().toLowerCase(),
        photo: form.values.photo ?? '',
        oldPassword: form.values.oldPassword || null,
        newPassword: form.values.newPassword || null,
      });

      toast.success(response.message);

      // Changing the username or password invalidates the current principal,
      // so the session has to be restarted.
      if (usernameChanged || passwordChanged) {
        await logout();
        navigate('/login', { replace: true });
        return;
      }

      await refresh();
      form.reset({
        ...form.values,
        oldPassword: '',
        newPassword: '',
        confirmPassword: '',
      });
    } catch (error) {
      toast.error(error.message);
    } finally {
      setSaving(false);
    }
  };

  const privilegeEntries = Object.entries(user?.privileges ?? {});

  return (
    <>
      <PageHeader
        eyebrow="Account"
        title="My profile"
        description="Update your sign-in details and photo."
        icon={<NavIcon name="settings" className="size-5" />}
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <form
          onSubmit={handleSubmit}
          noValidate
          className="rounded-panel bg-white p-5 shadow-panel ring-1 ring-slate-900/5 lg:col-span-2 dark:bg-slate-900 dark:ring-white/10"
        >
          <FormSection title="Details" columns={1}>
            <PhotoPicker
              value={form.values.photo}
              name={form.values.username}
              onChange={(photo) => form.setValue('photo', photo)}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Username" required {...form.field('username')} />
              <TextField label="Email" type="email" required {...form.field('email')} />
            </div>
          </FormSection>

          <FormSection
            title="Change password"
            description="Leave these blank to keep your current password."
            columns={1}
          >
            <TextField
              label="Current password"
              type="password"
              autoComplete="current-password"
              {...form.field('oldPassword')}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="New password"
                type="password"
                autoComplete="new-password"
                {...form.field('newPassword')}
              />
              <TextField
                label="Confirm new password"
                type="password"
                autoComplete="new-password"
                {...form.field('confirmPassword')}
              />
            </div>

            {(form.values.newPassword || form.values.username.trim() !== user?.username) && (
              <p className="rounded-lg bg-notice-50 p-3 text-xs font-medium text-notice-900 ring-1 ring-inset ring-notice-500/20 dark:bg-notice-900/25 dark:text-notice-500">
                Changing your username or password ends this session. You will be asked to sign in
                again.
              </p>
            )}
          </FormSection>

          <div className="flex justify-end border-t border-slate-200 pt-4 dark:border-slate-800">
            <Button type="submit" loading={saving}>
              Save changes
            </Button>
          </div>
        </form>

        <aside className="space-y-6">
          <section className="rounded-panel bg-white p-5 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <h2 className="mb-3 text-sm font-semibold text-slate-800 dark:text-slate-200">
              Your roles
            </h2>
            <div className="flex flex-wrap gap-1.5">
              {(user?.roles ?? []).map((role) => (
                <Badge key={role} tone="brand">
                  {role}
                </Badge>
              ))}
              {(user?.roles ?? []).length === 0 && (
                <p className="text-sm text-slate-500 dark:text-slate-400">No roles assigned.</p>
              )}
            </div>
          </section>

          <section className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="border-b border-slate-200 p-5 pb-3 dark:border-slate-800">
              <h2 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                Your permissions
              </h2>
              <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                What you may do in each module.
              </p>
            </div>

            <div className="scroll-x">
              <table className="w-full min-w-max text-sm">
                <thead>
                  <tr className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-400 dark:bg-slate-950/50">
                    <th scope="col" className="px-4 py-2 text-left">
                      Module
                    </th>
                    <th scope="col" className="px-2 py-2">
                      View
                    </th>
                    <th scope="col" className="px-2 py-2">
                      Add
                    </th>
                    <th scope="col" className="px-2 py-2">
                      Edit
                    </th>
                    <th scope="col" className="px-2 py-2 pr-4">
                      Del
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {privilegeEntries.map(([moduleName, flags]) => (
                    <tr key={moduleName}>
                      <td className="px-4 py-2 font-medium text-slate-700 dark:text-slate-300">
                        {moduleName}
                      </td>
                      <td className="px-2 py-2 text-center">
                        <BoolMark value={flags.select} label="View" />
                      </td>
                      <td className="px-2 py-2 text-center">
                        <BoolMark value={flags.insert} label="Add" />
                      </td>
                      <td className="px-2 py-2 text-center">
                        <BoolMark value={flags.update} label="Edit" />
                      </td>
                      <td className="px-2 py-2 pr-4 text-center">
                        <BoolMark value={flags.delete} label="Delete" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </aside>
      </div>
    </>
  );
}
