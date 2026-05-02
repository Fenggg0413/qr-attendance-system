import { useEffect, useMemo } from 'react';
import { api } from '../../services/api';
import { adminResources } from '../../constants/adminResources';
import { AdminDashboard } from './dashboard/AdminDashboard';
import { AdminCoursesPage } from './courses/AdminCoursesPage';
import { AdminDepartmentsPage } from './departments/AdminDepartmentsPage';
import { AdminResourcePage } from './resources/AdminResourcePage';

export function AdminPortal({ session, view, setBreadcrumb, logout }) {
  const client = useMemo(() => api.withToken(session.token), [session.token]);
  const config = adminResources[view];
  const titles = {
    dashboard: '数据总览',
    courses: '课程管理',
    departments: '院系管理',
  };
  const title = titles[view] ?? config?.title ?? '后台管理';

  useEffect(() => {
    setBreadcrumb(['首页', title]);
  }, [setBreadcrumb, title]);

  if (view === 'dashboard') return <AdminDashboard client={client} session={session} onAuthExpired={logout} />;
  if (view === 'courses') return <AdminCoursesPage client={client} />;
  if (view === 'departments') return <AdminDepartmentsPage client={client} />;
  if (config) {
    return <AdminResourcePage client={client} config={config} />;
  }
  return <AdminDashboard client={client} session={session} onAuthExpired={logout} />;
}
