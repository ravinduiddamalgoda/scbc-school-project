import { Navigate, Route, Routes } from 'react-router-dom';

import AppLayout from '@/components/layout/AppLayout';
import {
  RedirectIfAuthenticated,
  RequireAuth,
  RequirePrivilege,
} from '@/components/RouteGuards';

import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/DashboardPage';
import EmployeePage from '@/pages/EmployeePage';
import StudentPage from '@/pages/StudentPage';
import GuardianPage from '@/pages/GuardianPage';
import SubjectPage from '@/pages/SubjectPage';
import ClassPage from '@/pages/ClassPage';
import AttendancePage from '@/pages/AttendancePage';
import MarksPage from '@/pages/MarksPage';
import DistributionPage from '@/pages/DistributionPage';
import PaymentPage from '@/pages/PaymentPage';
import AcademicSetupPage from '@/pages/AcademicSetupPage';
import ReportsPage from '@/pages/ReportsPage';
import UserPage from '@/pages/UserPage';
import PrivilegePage from '@/pages/PrivilegePage';
import SbaPage from '@/pages/SbaPage';
import ParentPortalPage from '@/pages/ParentPortalPage';
import ProfilePage from '@/pages/ProfilePage';
import { ForbiddenPage, NotFoundPage } from '@/pages/StatusPages';

/**
 * Route table.
 *
 * Module routes are wrapped in RequirePrivilege so a user who types the URL
 * directly gets a clear 403 screen instead of an empty table. The server
 * re-checks the same privilege on every request.
 */
export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RedirectIfAuthenticated>
            <LoginPage />
          </RedirectIfAuthenticated>
        }
      />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/forbidden" element={<ForbiddenPage />} />

        <Route
          path="/students"
          element={
            <RequirePrivilege module="Student">
              <StudentPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/guardians"
          element={
            <RequirePrivilege module="Guardian">
              <GuardianPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/classes"
          element={
            <RequirePrivilege module="Class">
              <ClassPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/subjects"
          element={
            <RequirePrivilege module="Subject">
              <SubjectPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/attendance"
          element={
            <RequirePrivilege module="Attendance">
              <AttendancePage />
            </RequirePrivilege>
          }
        />

        {/*
          Marks are gated on the caller's role rather than on a privilege
          module, so the page guards itself; RequirePrivilege has no module to
          check here.
        */}
        <Route path="/marks" element={<MarksPage />} />

        <Route
          path="/distribution"
          element={
            <RequirePrivilege module="Student">
              <DistributionPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/sba"
          element={
            <RequirePrivilege module="SBA">
              <SbaPage />
            </RequirePrivilege>
          }
        />

        {/*
          The parent portal guards itself: access is "these three children",
          which no privilege module can express, so the server scopes every
          call to the guardian on the caller's own account and a staff account
          reaching it is refused there rather than here.
        */}
        <Route path="/my-children" element={<ParentPortalPage />} />

        <Route
          path="/payments"
          element={
            <RequirePrivilege module="Payment">
              <PaymentPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/academic-setup"
          element={
            <RequirePrivilege module="Class">
              <AcademicSetupPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/reports"
          element={
            <RequirePrivilege module="Report">
              <ReportsPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/employees"
          element={
            <RequirePrivilege module="Employee">
              <EmployeePage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/users"
          element={
            <RequirePrivilege module="User">
              <UserPage />
            </RequirePrivilege>
          }
        />

        <Route
          path="/privileges"
          element={
            <RequirePrivilege module="Privilage">
              <PrivilegePage />
            </RequirePrivilege>
          }
        />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
