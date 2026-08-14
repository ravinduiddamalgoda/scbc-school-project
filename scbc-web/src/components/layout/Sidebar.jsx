import { NavLink } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { NAV_SECTIONS, NavIcon } from './navigation';

function Brand() {
  return (
    <div className="flex items-center gap-3 px-5 py-5">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-base font-black text-white shadow-sm">
        S
      </span>
      <span className="min-w-0">
        <span className="block truncate text-sm font-bold tracking-tight text-slate-900 dark:text-slate-50">
          SCBC
        </span>
        <span className="block truncate text-xs text-slate-500 dark:text-slate-400">
          Buddhist College
        </span>
      </span>
    </div>
  );
}

/**
 * Primary navigation.
 *
 * Items are filtered against the privilege matrix, and a whole section
 * disappears when nothing in it is visible - so a user with only Student
 * rights never sees an empty "Administration" heading.
 */
export default function Sidebar({ onNavigate }) {
  const { can, hasRole } = useAuth();

  // An item is visible when its privilege module allows it, or - for the few
  // screens gated on who the user is rather than what they were granted - when
  // they hold one of the named roles.
  const visible = (item) => {
    if (item.roles) return hasRole(...item.roles);
    return !item.module || can(item.module).select;
  };

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter(visible),
  })).filter((section) => section.items.length > 0);

  return (
    <nav
      className="flex h-full flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
      aria-label="Main navigation"
    >
      <Brand />

      <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
        {sections.map((section) => (
          <div key={section.label} className="mb-5 last:mb-0">
            <p className="mb-1.5 px-3 text-[0.6875rem] font-semibold uppercase tracking-widest text-slate-400 dark:text-slate-500">
              {section.label}
            </p>

            <ul className="space-y-0.5">
              {section.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      [
                        'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition',
                        isActive
                          ? 'bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-300'
                          : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100',
                      ].join(' ')
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <span
                          className={
                            isActive ? 'text-brand-600 dark:text-brand-400' : 'text-slate-400'
                          }
                        >
                          <NavIcon name={item.icon} />
                        </span>
                        <span className="truncate">{item.label}</span>
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="border-t border-slate-200 px-5 py-3 dark:border-slate-800">
        <p className="text-[0.6875rem] leading-relaxed text-slate-400 dark:text-slate-500">
          Sri Chandananda Buddhist College
          <br />
          Management System v1.0
        </p>
      </div>
    </nav>
  );
}
