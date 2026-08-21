/**
 * The navigation model.
 *
 * `module` is the privilege module a link is gated on. Links without one are
 * always visible. This replaces the old approach of rendering every menu item
 * and then hiding elements by CSS class after an extra request.
 */
export const NAV_SECTIONS = [
  {
    label: 'Overview',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: 'grid' },
      // A parent account has no dashboard worth showing, and no other menu
      // item it may reach; this is the whole of its navigation.
      { to: '/my-children', label: 'My children', icon: 'home', roles: ['Parent'] },
    ],
  },
  {
    label: 'Students',
    items: [
      { to: '/students', label: 'Students', icon: 'students', module: 'Student' },
      { to: '/guardians', label: 'Guardians', icon: 'guardian', module: 'Guardian' },
    ],
  },
  {
    label: 'Academic',
    items: [
      { to: '/classes', label: 'Classes', icon: 'book', module: 'Class' },
      { to: '/subjects', label: 'Subjects', icon: 'award', module: 'Subject' },
      { to: '/attendance', label: 'Attendance', icon: 'calendar', module: 'Attendance' },
      // Gated on the caller's role rather than a privilege module, so no
      // `module` here: the page itself decides what a non-teacher may do.
      { to: '/marks', label: 'Marks', icon: 'award', roles: ['Admin', 'Principal', 'Teacher'] },
      { to: '/payments', label: 'Payments', icon: 'money', module: 'Payment' },
      { to: '/academic-setup', label: 'Academic setup', icon: 'settings', module: 'Class' },
      { to: '/reports', label: 'Reports', icon: 'chart', module: 'Report' },
      {
        to: '/distribution',
        label: 'Distribution & exams',
        icon: 'book',
        module: 'Student',
      },
      { to: '/sba', label: 'SBA marks', icon: 'clipboard', module: 'SBA' },
    ],
  },
  {
    label: 'Administration',
    items: [
      { to: '/employees', label: 'Employees', icon: 'employee', module: 'Employee' },
      { to: '/users', label: 'User accounts', icon: 'user', module: 'User' },
      { to: '/privileges', label: 'Permissions', icon: 'shield', module: 'Privilage' },
    ],
  },
];

/** Single-path icon set, kept inline so the app ships no icon-font dependency. */
export const ICON_PATHS = {
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  students: 'M22 10 12 5 2 10l10 5 10-5ZM6 12v5c0 1.7 2.7 3 6 3s6-1.3 6-3v-5',
  guardian:
    'M17 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9.5 7a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0ZM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  employee:
    'M20 7h-4V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2ZM10 5h4v2h-4Z',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  settings:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6h.09A1.65 1.65 0 0 0 10 3.09V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z',
  chart: 'M3 3v18h18M7 15l4-4 3 3 5-6',
  money: 'M12 1v22M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z',
  calendar:
    'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z',
  award: 'M12 15a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM8.2 13.9 7 23l5-3 5 3-1.2-9.1',
  star: 'm12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.8-6.2-3.3-6.2 3.3L7 14.2l-5-4.9 6.9-1L12 2Z',
  clipboard:
    'M9 2h6a1 1 0 0 1 1 1v2H8V3a1 1 0 0 1 1-1ZM8 5H6a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 12h6M9 16h4',
  home: 'M3 10.5 12 3l9 7.5M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5',
};

export function NavIcon({ name, className = 'size-4.5' }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d={ICON_PATHS[name] ?? ICON_PATHS.grid} />
    </svg>
  );
}
