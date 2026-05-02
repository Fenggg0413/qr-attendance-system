import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  BarChart3,
  Bell,
  BookOpen,
  Building2,
  CalendarDays,
  Check,
  ChevronLeft,
  ClipboardCheck,
  Clock,
  Cloud,
  Download,
  Eye,
  EyeOff,
  FileText,
  Fullscreen,
  KeyRound,
  Landmark,
  Loader2,
  LogOut,
  Maximize2,
  Menu,
  Minimize2,
  PenLine,
  PieChart,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import './styles.css';
import { api, ApiError } from './services/api';
import { adminLabels, adminNav, adminResources } from './constants/adminResources';
import { fallbackTerms, schedulePeriods, scheduleWeekDays, weekDays } from './constants/schedule';
import {
  courseNameText,
  csvCell,
  displayAdminRow,
  formatAdminCell,
  formatCourseSchedule,
  formatDate,
  formatSeconds,
  formatToday,
  methodText,
  roleText,
  sortableAriaSort,
  sourceText,
  statusText,
  teacherOptionText,
} from './utils/format';
import {
  filterAdminCourses,
  filterClassroomRows,
  filterRows,
  filterStudentRows,
  filterTeacherRows,
  handleColumnSort,
  sessionTotals,
  sortRows,
} from './utils/filters';
import { adminFieldValue, emptyAdminForm, normalizeAdminForm } from './utils/adminForm';
import { useTableSort } from './hooks/useTableSort';
import { TopBar } from './components/TopBar';
import { StatCard } from './components/StatCard';
import { AdminPageHead } from './components/AdminPageHead';
import { AdminPagination } from './components/AdminPagination';
import { AdminTableToolbar } from './components/AdminTableToolbar';
import { SortableTableHeader } from './components/SortableTableHeader';
import { AdminDataTable } from './components/AdminDataTable';
import { AdminResourceFormDialog } from './components/AdminResourceFormDialog';
import { LoginView } from './features/login/LoginView';
import { AttendanceModal } from './features/teacher/AttendanceModal';
import { MakeupDialog } from './features/teacher/MakeupDialog';
import { RecordsDrawer } from './features/teacher/RecordsDrawer';
import { RecordsTable } from './features/teacher/RecordsTable';
import { SessionsTable } from './features/teacher/SessionsTable';
import { StudentsPanel } from './features/teacher/StudentsPanel';
import { VisualPanel } from './features/teacher/VisualPanel';
import { CoursesWorkspace } from './features/teacher/CoursesWorkspace';
import { CourseDetail } from './features/teacher/CourseDetail';
import { TodayBoard } from './features/teacher/TodayBoard';
import { TodayCard } from './features/teacher/TodayCard';
import { TeacherProfile } from './features/teacher/TeacherProfile';
import { TeacherLeaveRequests } from './features/teacher/TeacherLeaveRequests';
import { TeacherPortal } from './features/teacher/TeacherPortal';
import { AdminDashboard } from './features/admin/dashboard/AdminDashboard';
import { AdminResourcePage } from './features/admin/resources/AdminResourcePage';
import { AdminCoursesPage } from './features/admin/courses/AdminCoursesPage';
import { AdminDepartmentsPage } from './features/admin/departments/AdminDepartmentsPage';

function App() {
  const [session, setSession] = useState(() => {
    const raw = localStorage.getItem('qr-attendance-session');
    return raw ? JSON.parse(raw) : null;
  });
  const [teacherView, setTeacherView] = useState('today');
  const [adminView, setAdminView] = useState('dashboard');
  const [breadcrumb, setBreadcrumb] = useState(['首页', '今日课表']);
  const [collapsed, setCollapsed] = useState(false);

  function onLogin(nextSession) {
    localStorage.setItem('qr-attendance-session', JSON.stringify(nextSession));
    setSession(nextSession);
    setTeacherView('today');
    setAdminView('dashboard');
  }

  function logout() {
    localStorage.removeItem('qr-attendance-session');
    setSession(null);
    setTeacherView('today');
    setAdminView('dashboard');
  }

  if (!session) return <LoginView onLogin={onLogin} />;

  const isTeacher = session.user.role === 'TEACHER';

  return (
    <div className={collapsed ? 'shell collapsed' : 'shell'}>
      <aside className="side">
        <div>
          <div className="brand">
            <span className="brandMark"><Cloud size={20} fill="currentColor" /></span>
            <span className="brandText">
              云考勤
              <small>考勤管理系统</small>
            </span>
          </div>
          {isTeacher ? (
            <nav className="sideNav" aria-label="教师导航">
              <button className={teacherView === 'today' ? 'active' : ''} onClick={() => setTeacherView('today')}>
                <CalendarDays size={17} />
                <span>今日课表</span>
              </button>
              <button className={teacherView === 'courses' ? 'active' : ''} onClick={() => setTeacherView('courses')}>
                <BookOpen size={17} />
                <span>我的课程</span>
              </button>
              <button className={teacherView === 'leave-requests' ? 'active' : ''} onClick={() => setTeacherView('leave-requests')}>
                <ClipboardCheck size={17} />
                <span>申报审核</span>
              </button>
              <button className={teacherView === 'profile' ? 'active' : ''} onClick={() => setTeacherView('profile')}>
                <UserRound size={17} />
                <span>个人中心</span>
              </button>
            </nav>
          ) : (
            <nav className="sideNav" aria-label="管理员导航">
              <span>管理</span>
              {adminNav.map(([key, title, Icon]) => {
                return (
                  <button key={key} className={adminView === key ? 'active' : ''} onClick={() => setAdminView(key)}>
                    <Icon size={17} />
                    <span>{title}</span>
                  </button>
                );
              })}
            </nav>
          )}
        </div>
        <div className="sideUser">
          <div className="identity">
            <UserRound size={18} />
            <span>
              {session.user.displayName}
              <small>{roleText(session.user.role)}</small>
            </span>
          </div>
          <button className="iconButton" onClick={logout} aria-label="快捷退出登录">
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <main className="main">
        <TopBar
          breadcrumb={breadcrumb}
          collapsed={collapsed}
          onToggleSide={() => setCollapsed((value) => !value)}
          onLogout={logout}
        />
        {isTeacher ? (
          <TeacherPortal
            session={session}
            view={teacherView}
            setView={setTeacherView}
            setBreadcrumb={setBreadcrumb}
            logout={logout}
          />
        ) : (
          <AdminPortal session={session} view={adminView} setBreadcrumb={setBreadcrumb} logout={logout} />
        )}
      </main>
    </div>
  );
}

function AdminPortal({ session, view, setBreadcrumb, logout }) {
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

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

export default App;
