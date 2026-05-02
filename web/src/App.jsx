import { useState } from 'react';
import { BookOpen, CalendarDays, ClipboardCheck, Cloud, LogOut, UserRound } from 'lucide-react';
import { adminNav } from './constants/adminResources';
import { roleText } from './utils/format';
import { TopBar } from './components/TopBar';
import { LoginView } from './features/login/LoginView';
import { TeacherPortal } from './features/teacher/TeacherPortal';
import { AdminPortal } from './features/admin/AdminPortal';

export default function App() {
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
