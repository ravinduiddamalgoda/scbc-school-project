import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

/**
 * Shell for every authenticated page: a persistent sidebar on large screens,
 * a slide-over drawer below `lg`, and the routed page in the content column.
 */
export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  // Navigating on mobile should dismiss the drawer.
  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!sidebarOpen) return undefined;
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setSidebarOpen(false);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [sidebarOpen]);

  return (
    <div className="flex h-full">
      {/* Persistent navigation from lg upward. */}
      <aside className="hidden w-64 shrink-0 lg:block">
        <div className="fixed inset-y-0 left-0 w-64">
          <Sidebar />
        </div>
      </aside>

      {/* Drawer navigation below lg. */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="animate-fade-in absolute inset-0 bg-slate-900/50 backdrop-blur-[2px]"
            onClick={() => setSidebarOpen(false)}
            aria-hidden="true"
          />
          <div className="animate-slide-in absolute inset-y-0 left-0 w-64 -translate-x-0">
            <Sidebar onNavigate={() => setSidebarOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenSidebar={() => setSidebarOpen(true)} />

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-7xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
