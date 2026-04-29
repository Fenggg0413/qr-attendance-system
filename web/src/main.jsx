import React, { useEffect, useMemo, useState } from 'react';
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
  EyeOff,
  FileText,
  Fullscreen,
  KeyRound,
  Landmark,
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

const adminResources = {
  students: {
    title: '学生管理',
    endpoint: '/admin/students',
    icon: UserRound,
    fields: [
      ['name', '姓名', { required: true, placeholder: '请输入学生姓名' }],
      ['username', '账号', { required: true, placeholder: '请输入登录账号' }],
      ['password', '初始密码', { placeholder: '留空则使用默认密码 (123456)' }],
      ['studentNo', '学号', { required: true, placeholder: '请输入学号' }],
      ['grade', '年级', { required: true, type: 'year' }],
      ['departmentId', '所属院系', { required: true }],
    ],
    columns: ['display_index', 'name', 'username', 'student_no', 'department_name', 'grade'],
  },
  teachers: {
    title: '教师管理',
    endpoint: '/admin/teachers',
    icon: UsersRound,
    fields: [
      ['name', '姓名', { required: true, placeholder: '请输入教师姓名' }],
      ['username', '账号', { required: true, placeholder: '请输入登录账号' }],
      ['password', '初始密码', { placeholder: '留空则使用默认密码 (123456)' }],
      ['departmentId', '所属院系', { required: true }],
    ],
    columns: ['display_index', 'name', 'username', 'department_name'],
  },
  classrooms: {
    title: '教室管理',
    endpoint: '/admin/classrooms',
    icon: Building2,
    fields: [
      ['name', '教室名称'],
      ['building', '教学楼'],
      ['capacity', '容量'],
    ],
    columns: ['display_index', 'name', 'building', 'capacity'],
    labels: { name: '教室名称' },
  },
};

const adminNav = [
  ['dashboard', '仪表盘', BarChart3],
  ['students', '学生管理', UserRound],
  ['teachers', '教师管理', UsersRound],
  ['classrooms', '教室管理', Building2],
  ['courses', '课程管理', BookOpen],
  ['departments', '院系管理', Landmark],
];

const adminLabels = {
  id: 'ID',
  display_index: '序号',
  name: '姓名',
  username: '账号',
  department: '院系',
  department_name: '所属院系',
  student_no: '学号',
  class_name: '班级',
  grade: '年级',
  code: '课程代码',
  course_name: '课程',
  teacher_name: '教师',
  term: '学期',
  building: '教学楼',
  capacity: '容量',
  student_name: '学生',
  source: '来源',
  status: '状态',
  checked_in_at: '签到时间',
  session_id: '场次',
  total: '应到',
  present: '已到',
  excused: '请假',
  absent: '缺勤',
  reason: '原因',
  created_at: '提交时间',
};

function roleText(role) {
  return { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }[role] ?? '未知角色';
}

function statusText(status) {
  return {
    PRESENT: '已到',
    ABSENT: '缺勤',
    LATE: '迟到',
    EXCUSED: '已请假',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    OPEN: '进行中',
    CLOSED: '已结束',
  }[status] ?? '未知状态';
}

function sourceText(source) {
  return { QR: '扫码', CODE: '验证码', MANUAL: '手动', LEAVE: '申报' }[source] ?? '-';
}

function methodText(method) {
  return { QR: '二维码', CODE: '数字验证码', MANUAL: '手动点名' }[method] ?? '二维码';
}

function courseNameText(name) {
  return name ?? '-';
}

function teacherOptionText(teacher) {
  return `${teacher.name}（${teacher.username ?? `ID ${teacher.id}`}） · ${teacher.department_name ?? teacher.department ?? '未设置院系'}`;
}

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const scheduleWeekDays = weekDays.slice(0, 5);
const schedulePeriods = [
  { period: 1, time: '08:00\n08:45' },
  { period: 2, time: '08:50\n09:35' },
  { period: 3, time: '09:50\n10:35' },
  { period: 4, time: '10:40\n11:25' },
  { period: 5, time: '11:30\n12:15' },
  { period: 6, time: '13:45\n14:30' },
  { period: 7, time: '14:35\n15:20' },
  { period: 8, time: '15:35\n16:20' },
  { period: 9, time: '16:25\n17:10' },
];
const fallbackTerms = [
  { value: '2025-2026学年 秋季学期', label: '2025-2026学年 秋季学期' },
  { value: '2025-2026学年 春季学期', label: '2025-2026学年 春季学期' },
  { value: '2026-2027学年 秋季学期', label: '2026-2027学年 秋季学期' },
  { value: '2026-2027学年 春季学期', label: '2026-2027学年 春季学期' },
];

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

function TopBar({ breadcrumb, collapsed, onToggleSide, onLogout }) {
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  async function toggleFullScreen() {
    if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
      await document.documentElement.requestFullscreen();
    } else if (document.exitFullscreen) {
      await document.exitFullscreen();
    }
  }

  return (
    <div className="topBar">
      <div className="topLeft">
        <button className="iconButton" onClick={onToggleSide} aria-label={collapsed ? '展开侧栏' : '折叠侧栏'}>
          <Menu size={18} />
        </button>
        <div className="breadcrumb">
          {breadcrumb.map((item, index) => (
            <React.Fragment key={`${item}-${index}`}>
              {index > 0 && <b>/</b>}
              <span className={index === breadcrumb.length - 1 ? 'current' : ''}>{item}</span>
            </React.Fragment>
          ))}
        </div>
      </div>
      <div className="topActions">
        <button className="iconButton" onClick={toggleFullScreen} aria-label="全屏">
          <Fullscreen size={17} />
        </button>
        <div className="popoverWrap">
          <button className="iconButton" onClick={() => setNoticeOpen((value) => !value)} aria-label="通知">
            <Bell size={17} />
            <i className="dot" />
          </button>
          {noticeOpen && <div className="miniPopover">暂无新的系统通知</div>}
        </div>
        <div className="popoverWrap">
          <button className="avatarButton" onClick={() => setMenuOpen((value) => !value)} aria-label="头像菜单">
            <UserRound size={17} />
          </button>
          {menuOpen && (
            <div className="miniPopover menuPopover">
              <button className="plainAction" onClick={onLogout}><LogOut size={15} />退出登录</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function LoginView({ onLogin }) {
  const [form, setForm] = useState({ username: 'admin', password: 'admin123' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      onLogin(await api.login(form));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login">
      <form className="loginPanel" onSubmit={submit}>
        <h1>动态二维码考勤</h1>
        <label>
          账号
          <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
        </label>
        <label>
          密码
          <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </label>
        {error && <div className="error">{error}</div>}
        <button disabled={loading}>{loading ? '登录中' : '登录'}</button>
        <p className="hint">默认账号：管理员 admin/admin123</p>
      </form>
    </main>
  );
}

function TeacherPortal({ session, view, setView, setBreadcrumb, logout }) {
  const client = useMemo(() => api.withToken(session.token), [session.token]);
  const [courses, setCourses] = useState([]);
  const [coursesError, setCoursesError] = useState('');
  const [selectedCourse, setSelectedCourse] = useState(null);
  const [activeTab, setActiveTab] = useState('records');
  const [detail, setDetail] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [students, setStudents] = useState([]);
  const [liveSession, setLiveSession] = useState(null);
  const [drawerSession, setDrawerSession] = useState(null);
  const [drawerRecords, setDrawerRecords] = useState([]);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    if (view === 'today') setBreadcrumb(['首页', '今日课表']);
    else if (view === 'leave-requests') setBreadcrumb(['首页', '申报审核']);
    else if (view === 'profile') setBreadcrumb(['首页', '个人中心']);
    else setBreadcrumb(['首页', selectedCourse ? '课程详情' : '我的课程']);
  }, [setBreadcrumb, selectedCourse, view]);

  useEffect(() => {
    let cancelled = false;
    client.get('/teacher/courses')
      .then((data) => {
        if (!cancelled) setCourses(data);
      })
      .catch((err) => {
        if (!cancelled) setCoursesError(err.message ?? '课程加载失败');
      });
    return () => {
      cancelled = true;
    };
  }, [client]);

  useEffect(() => {
    if (view !== 'courses' || !selectedCourse) return undefined;
    let cancelled = false;
    setLoadingDetail(true);
    Promise.all([
      client.get(`/teacher/courses/${selectedCourse.id}`),
      client.get(`/teacher/courses/${selectedCourse.id}/attendance-sessions`),
    ])
      .then(([nextDetail, nextSessions]) => {
        if (!cancelled) {
          setDetail(nextDetail);
          setSessions(nextSessions);
        }
      })
      .catch((err) => {
        if (!cancelled) setCoursesError(err.message ?? '课程详情加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoadingDetail(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, selectedCourse, view]);

  useEffect(() => {
    if (view !== 'courses' || activeTab !== 'students' || !selectedCourse) return undefined;
    let cancelled = false;
    client.get(`/teacher/courses/${selectedCourse.id}/students`).then((data) => {
      if (!cancelled) setStudents(data);
    });
    return () => {
      cancelled = true;
    };
  }, [activeTab, client, selectedCourse, view]);

  function openDetail(course) {
    setView('courses');
    setSelectedCourse(course);
    setActiveTab('records');
  }

  async function refreshCourseData() {
    if (!selectedCourse) return;
    setSessions(await client.get(`/teacher/courses/${selectedCourse.id}/attendance-sessions`));
    if (activeTab === 'students') {
      setStudents(await client.get(`/teacher/courses/${selectedCourse.id}/students`));
    }
  }

  async function deleteSession(sessionId) {
    await client.delete(`/teacher/attendance-sessions/${sessionId}`);
    await refreshCourseData();
  }

  async function openRecordsDrawer(sessionRow) {
    setDrawerSession(sessionRow);
    setDrawerRecords(await client.get(`/teacher/attendance-sessions/${sessionRow.id}/records`));
  }

  if (view === 'leave-requests') {
    return <TeacherLeaveRequests client={client} onAuthExpired={logout} />;
  }

  if (view === 'profile') {
    return <TeacherProfile client={client} logout={logout} />;
  }

  if (view === 'today') {
    return (
      <>
        <TodayBoard client={client} courses={courses} onLive={setLiveSession} />
        {liveSession && (
          <AttendanceModal
            client={client}
            session={liveSession}
            headline={liveSession.headline}
            subline={liveSession.subline}
            onClose={() => setLiveSession(null)}
          />
        )}
      </>
    );
  }

  return (
    <>
      {!selectedCourse ? (
        <CoursesWorkspace
          courses={courses}
          error={coursesError}
          onOpenDetail={openDetail}
        />
      ) : (
        <CourseDetail
          client={client}
          course={detail ?? selectedCourse}
          sessions={sessions}
          students={students}
          activeTab={activeTab}
          loading={loadingDetail}
          onBack={() => setSelectedCourse(null)}
          onTab={setActiveTab}
          onOpenRecords={openRecordsDrawer}
          onStudentsChange={setStudents}
          onDeleteSession={deleteSession}
        />
      )}
      {drawerSession && (
        <RecordsDrawer
          session={drawerSession}
          records={drawerRecords}
          onClose={() => {
            setDrawerSession(null);
            setDrawerRecords([]);
          }}
        />
      )}
    </>
  );
}

function CoursesWorkspace({ courses, error, onOpenDetail }) {
  const [keyword, setKeyword] = useState('');
  const [semester, setSemester] = useState('全部学期');
  const semesters = ['全部学期', ...Array.from(new Set(courses.map((course) => course.semester).filter(Boolean)))];
  const normalized = keyword.trim().toLowerCase();
  const filtered = courses.filter((course) => {
    const inSemester = semester === '全部学期' || course.semester === semester;
    const text = `${course.name} ${course.code} ${course.class_name}`.toLowerCase();
    return inSemester && (!normalized || text.includes(normalized));
  });

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>我的课程</h1>
        </div>
        <div className="summaryStrip">
          <span><BookOpen size={16} />课程 {courses.length}</span>
        </div>
      </section>

      <section className="toolbar panel">
        <label>
          学期
          <select value={semester} onChange={(event) => setSemester(event.target.value)}>
            {semesters.map((item) => <option key={item}>{item}</option>)}
          </select>
        </label>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="搜索课程名称、代码或班级"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </span>
        </label>
      </section>

      {error && <div className="error">{error}</div>}
      <section className="courseCards">
        {filtered.map((course) => (
          <article className="courseCard" key={course.id}>
            <div>
              <span className="eyebrow">{course.semester}</span>
              <h2>{courseNameText(course.name)}</h2>
              <p>{course.code} · {course.class_name}</p>
            </div>
            <div className="cardActions">
              <button onClick={() => onOpenDetail(course)}>查看详情</button>
            </div>
          </article>
        ))}
        {!filtered.length && <div className="empty panel">没有匹配的课程</div>}
      </section>
    </div>
  );
}

function CourseDetail({ client, course, sessions, students, activeTab, loading, onBack, onTab, onOpenRecords, onStudentsChange, onDeleteSession }) {
  const totals = sessionTotals(sessions);
  const latest = sessions[0];

  function exportCsv() {
    const rows = [
      ['考勤编号', '开始时间', '方式', '状态', '已到', '缺勤', '请假', '迟到'],
      ...sessions.map((row) => [
        row.id,
        formatDate(row.started_at),
        methodText(row.method),
        statusText(row.status),
        row.present_count ?? 0,
        row.absent_count ?? 0,
        row.excused_count ?? 0,
        row.late_count ?? 0,
      ]),
    ];
    const csv = rows.map((row) => row.map(csvCell).join(',')).join('\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${course.name}-attendance.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="teacherPage">
      <section className="pageHead detailHead">
        <button className="iconButton" onClick={onBack} aria-label="返回课程列表"><ChevronLeft size={18} /></button>
        <div>
          <h1>{courseNameText(course.name)}</h1>
          <p>{course.code} · {course.class_name} · {course.semester}</p>
        </div>
        <span className="hint">前往「今日课表」按节次发起考勤</span>
      </section>

      <section className="statGrid">
        <StatCard icon={<UsersRound />} value={course.student_count ?? 0} label="学生数" />
        <StatCard icon={<ClipboardCheck />} value={sessions.length} label="考勤次数" />
        <StatCard icon={<Check />} value={totals.present} label="累计已到" />
        <StatCard icon={<Clock />} value={totals.absent} label="累计缺勤" />
      </section>

      <div className="tabs" role="tablist" aria-label="课程详情">
        <button role="tab" aria-selected={activeTab === 'records'} className={activeTab === 'records' ? 'active' : ''} onClick={() => onTab('records')}>考勤记录</button>
        <button role="tab" aria-selected={activeTab === 'students'} className={activeTab === 'students' ? 'active' : ''} onClick={() => onTab('students')}>学生名单</button>
        <button role="tab" aria-selected={activeTab === 'visual'} className={activeTab === 'visual' ? 'active' : ''} onClick={() => onTab('visual')}>可视化</button>
      </div>

      {activeTab === 'records' && (
        <section className="panel">
          <div className="panelHead">
            <h2><FileText size={17} />考勤记录</h2>
            <div className="actions">
              <button className="ghost" disabled={!sessions.length} onClick={exportCsv}><Download size={16} />导出数据</button>
            </div>
          </div>
          {loading ? <p className="hint">加载中</p> : <SessionsTable sessions={sessions} onOpenRecords={onOpenRecords} onDelete={onDeleteSession} />}
        </section>
      )}

      {activeTab === 'students' && (
        <StudentsPanel client={client} students={students} onStudentsChange={onStudentsChange} />
      )}

      {activeTab === 'visual' && (
        <VisualPanel sessions={sessions} latest={latest} students={students} />
      )}
    </div>
  );
}

function SessionsTable({ sessions, onOpenRecords, onDelete }) {
  if (!sessions.length) return <div className="empty">暂无考勤记录</div>;
  return (
    <div className="tableWrap">
      <table>
        <thead>
          <tr>
            <th>开始时间</th>
            <th>方式</th>
            <th>状态</th>
            <th>已到</th>
            <th>缺勤</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((row) => (
            <tr key={row.id}>
              <td>{formatDate(row.started_at)}</td>
              <td>{methodText(row.method)}</td>
              <td><span className={`pill ${row.status === 'OPEN' ? 'live' : ''}`}>{statusText(row.status)}</span></td>
              <td>{row.present_count ?? 0}</td>
              <td>{row.absent_count ?? 0}</td>
              <td>
                <div className="actions">
                  <button className="ghost" onClick={() => onOpenRecords(row)}>查看明细</button>
                  {onDelete && (
                    <button className="ghost danger" onClick={() => { if (window.confirm('确定删除该考勤记录？删除后不可恢复。')) onDelete(row.id); }}>
                    <Trash2 size={14} />删除
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StudentsPanel({ client, students, onStudentsChange }) {
  const [savingId, setSavingId] = useState(null);
  const [keyword, setKeyword] = useState('');
  const sorted = [...students].sort((a, b) => `${a.student_no}${a.name}`.localeCompare(`${b.student_no}${b.name}`, 'zh-Hans-CN'));
  const normalized = keyword.trim().toLowerCase();
  const filtered = normalized ? sorted.filter((s) => s.name.toLowerCase().includes(normalized) || s.student_no.toLowerCase().includes(normalized)) : sorted;

  async function save(student) {
    setSavingId(student.id);
    const saved = await client.put(`/teacher/students/${student.id}/note`, { note: student.note ?? '' });
    onStudentsChange(students.map((item) => (item.id === student.id ? { ...item, note: saved.note, saved: true } : item)));
    setSavingId(null);
  }

  return (
    <section className="panel">
      <div className="panelHead">
        <h2><UsersRound size={17} />学生名单管理</h2>
        <div className="panelHeadRight">
          <label className="searchField compact">
            <span>
              <Search size={15} />
              <input placeholder="搜索姓名或学号" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </span>
          </label>
          <span className="muted">{filtered.length} / {students.length} 人</span>
        </div>
      </div>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>学号</th>
              <th>姓名</th>
              <th>教师备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((student) => (
              <tr key={student.id}>
                <td>{student.student_no}</td>
                <td>{student.name}</td>
                <td>
                  <input
                    value={student.note ?? ''}
                    onChange={(event) => {
                      onStudentsChange(students.map((item) => (item.id === student.id ? { ...item, note: event.target.value, saved: false } : item)));
                    }}
                  />
                </td>
                <td>
                  <button className="ghost" disabled={savingId === student.id} onClick={() => save(student)}>
                    <Save size={15} />{student.saved ? '已保存' : '保存'}
                  </button>
                </td>
              </tr>
            ))}
            {!filtered.length && <tr><td colSpan="4" className="empty">没有匹配的学生</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function VisualPanel({ sessions, students }) {
  const riskRows = students.length ? students : [{ id: 'empty', name: '暂无学生', student_no: '-', risk: 0 }];
  return (
    <section className="visualGrid">
      <div className="panel">
        <div className="panelHead">
          <h2><BarChart3 size={17} />出勤率趋势</h2>
          <span className="muted">最近 {Math.min(sessions.length, 6)} 次</span>
        </div>
        <div className="trendBars">
          {sessions.slice(0, 6).reverse().map((session) => {
            const total = Number(session.total_count ?? 0);
            const rate = total ? Math.round((Number(session.present_count ?? 0) / total) * 100) : 0;
            return (
              <div className="trendItem" key={session.id}>
                <i style={{ height: `${Math.max(8, rate)}%` }} />
                <span>{rate}%</span>
              </div>
            );
          })}
          {!sessions.length && <p className="hint">暂无趋势数据</p>}
        </div>
      </div>
      <div className="panel">
        <div className="panelHead">
          <h2>高风险缺勤学生排行</h2>
          <span className="muted">按学号近似排序</span>
        </div>
        <div className="riskList">
          {riskRows.slice(0, 5).map((student, index) => (
            <div className="riskRow" key={student.id}>
              <span>{index + 1}</span>
              <strong>{student.name}</strong>
              <em>{student.student_no}</em>
              <b style={{ width: `${Math.max(12, 72 - index * 12)}%` }} />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function TodayBoard({ client, courses, onLive }) {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [now, setNow] = useState(() => Date.now());
  const [makeupOpen, setMakeupOpen] = useState(false);

  const refresh = async () => {
    try {
      const next = await client.get('/teacher/today');
      setItems(Array.isArray(next) ? next : []);
      setError('');
    } catch (err) {
      setError(err.message ?? '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, 30000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const summary = useMemo(() => {
    const total = items.length;
    const running = items.filter((it) => it.phase === 'RUNNING').length;
    const ended = items.filter((it) => it.phase === 'ENDED').length;
    return { total, running, ended, pending: total - running - ended };
  }, [items]);

  async function startSession(card) {
    setBusyId(card.slotId);
    try {
      const created = await client.post(`/teacher/schedule-slots/${card.slotId}/attendance-sessions`, { method: 'QR' });
      const headline = `${courseNameText(card.courseName)} · 第 ${card.periodStart}${card.periodEnd > card.periodStart ? `-${card.periodEnd}` : ''} 节`;
      const subline = `${card.className || ''}${card.classroomName ? ` · ${card.classroomName}` : ''}`;
      onLive({ id: created.id, endsAt: created.endsAt ?? created.ends_at, headline, subline });
      await refresh();
    } catch (err) {
      setError(err.message ?? '发起考勤失败');
    } finally {
      setBusyId(null);
    }
  }

  function viewSession(card) {
    if (!card.session) return;
    const headline = `${courseNameText(card.courseName)} · 第 ${card.periodStart}${card.periodEnd > card.periodStart ? `-${card.periodEnd}` : ''} 节`;
    const subline = `${card.className || ''}${card.classroomName ? ` · ${card.classroomName}` : ''}`;
    onLive({ id: card.session.id, endsAt: card.session.ends_at ?? card.session.endsAt, headline, subline });
  }

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>今日课表</h1>
          <p>{formatToday()}</p>
        </div>
        <div className="summaryStrip">
          <span><CalendarDays size={16} />共 {summary.total} 节</span>
          <span><ClipboardCheck size={16} />进行中 {summary.running}</span>
          <span><Check size={16} />已结束 {summary.ended}</span>
          <button className="ghost" onClick={() => setMakeupOpen(true)}><Plus size={16} />补考勤</button>
        </div>
      </section>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <div className="panel hint">加载中</div>
      ) : !items.length ? (
        <div className="empty panel">今天没有排课，可使用「补考勤」记录调课。</div>
      ) : (
        <section className="courseCards">
          {items.map((card) => (
            <TodayCard
              key={card.slotId}
              card={card}
              now={now}
              busy={busyId === card.slotId}
              onStart={() => startSession(card)}
              onView={() => viewSession(card)}
            />
          ))}
        </section>
      )}

      {makeupOpen && (
        <MakeupDialog
          client={client}
          courses={courses}
          onClose={() => setMakeupOpen(false)}
          onCreated={async (created, card) => {
            setMakeupOpen(false);
            onLive({
              id: created.id,
              endsAt: created.endsAt ?? created.ends_at,
              headline: `${courseNameText(card.courseName)} · 补考勤`,
              subline: `${card.weekday} 第 ${card.period} 节${card.classroom_name ? ` · ${card.classroom_name}` : ''}`,
            });
            await refresh();
          }}
        />
      )}
    </div>
  );
}

function TodayCard({ card, now, busy, onStart, onView }) {
  const startMs = new Date(card.startTime).getTime();
  const endMs = new Date(card.endTime).getTime();
  const periodLabel = card.periodEnd > card.periodStart ? `第 ${card.periodStart}-${card.periodEnd} 节` : `第 ${card.periodStart} 节`;
  const startLabel = new Date(card.startTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
  const endLabel = new Date(card.endTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });

  let statusPill = null;
  let cta = null;
  if (card.phase === 'BEFORE') {
    const seconds = Math.max(0, Math.floor((startMs - now) / 1000));
    statusPill = <span className="pill">未到上课时间</span>;
    cta = <button className="ghost" disabled>距上课 {formatSeconds(seconds)}</button>;
  } else if (card.phase === 'RUNNING') {
    const seconds = Math.max(0, Math.floor((endMs - now) / 1000));
    statusPill = <span className="pill live">进行中 · 剩 {formatSeconds(seconds)}</span>;
    cta = <button onClick={onView}>查看二维码</button>;
  } else if (card.phase === 'ENDED') {
    statusPill = <span className="pill">已结束</span>;
    cta = card.session ? <button className="ghost" onClick={onView}>查看记录</button> : <button className="ghost" disabled>未发起</button>;
  } else {
    statusPill = <span className="pill live">可发起</span>;
    cta = <button disabled={busy} onClick={onStart}><Plus size={16} />开始考勤</button>;
  }

  return (
    <article className="courseCard">
      <div>
        <span className="eyebrow">{periodLabel} · {startLabel}-{endLabel}</span>
        <h2>{courseNameText(card.courseName)}</h2>
        <p>{[card.className, card.classroomName].filter(Boolean).join(' · ') || '—'}</p>
        <div style={{ marginTop: 8 }}>{statusPill}</div>
        {card.session && (
          <p className="hint" style={{ marginTop: 8 }}>
            {card.session.kind === 'MAKEUP' ? '补考勤 · ' : ''}
            已签到 {Number(card.session.presentCount ?? 0)} / {Number(card.session.totalCount ?? 0)}
          </p>
        )}
      </div>
      <div className="cardActions">{cta}</div>
    </article>
  );
}

function MakeupDialog({ client, courses, onClose, onCreated }) {
  const [courseId, setCourseId] = useState(courses[0]?.id ?? '');
  const [slots, setSlots] = useState([]);
  const [slotId, setSlotId] = useState('');
  const [reason, setReason] = useState('');
  const [duration, setDuration] = useState(30);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!courseId) {
      setSlots([]);
      return;
    }
    let cancelled = false;
    client.get(`/teacher/courses/${courseId}`).then((data) => {
      if (cancelled) return;
      const list = Array.isArray(data?.scheduleSlots) ? data.scheduleSlots : [];
      setSlots(list);
      setSlotId(list[0]?.id ?? '');
    }).catch((err) => {
      if (!cancelled) setError(err.message ?? '加载排课失败');
    });
    return () => {
      cancelled = true;
    };
  }, [client, courseId]);

  async function submit(event) {
    event.preventDefault();
    setError('');
    if (!slotId) {
      setError('请选择目标排课');
      return;
    }
    if (!reason.trim()) {
      setError('请填写补考勤理由');
      return;
    }
    setBusy(true);
    try {
      const created = await client.post('/teacher/attendance-sessions/makeup', {
        slotId: Number(slotId),
        reason: reason.trim(),
        durationMinutes: Number(duration),
      });
      const slot = slots.find((s) => Number(s.id) === Number(slotId));
      const card = {
        courseName: courses.find((c) => Number(c.id) === Number(courseId))?.name,
        weekday: slot?.weekday ?? '',
        period: slot?.period ?? '',
        classroom_name: slot?.classroom_name ?? '',
      };
      await onCreated(created, card);
    } catch (err) {
      setError(err.message ?? '补考勤失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modalLayer">
      <section className="modal" role="dialog" aria-modal="true" aria-label="补考勤">
        <div className="modalHead">
          <div>
            <h2>补考勤</h2>
            <p>用于调课、临时改时间等特殊情况</p>
          </div>
          <button className="iconButton" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>
        <form className="modalScroll" onSubmit={submit}>
          <div className="modalBody">
            <label>
              课程
              <select value={courseId} onChange={(event) => setCourseId(Number(event.target.value))}>
                {courses.map((c) => <option key={c.id} value={c.id}>{c.name}（{c.code}）</option>)}
              </select>
            </label>
            <label>
              目标排课
              <select value={slotId} onChange={(event) => setSlotId(Number(event.target.value))} disabled={!slots.length}>
                {!slots.length && <option value="">该课程暂无排课</option>}
                {slots.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.weekday} 第 {s.period} 节{s.classroom_name ? ` · ${s.classroom_name}` : ''}
                  </option>
                ))}
              </select>
            </label>
            <label>
              开放时长（分钟）
              <input type="number" min="1" max="120" value={duration} onChange={(event) => setDuration(Number(event.target.value))} />
            </label>
            <label>
              理由
              <textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="例如：周一调到本日补课" />
            </label>
            {error && <div className="error">{error}</div>}
            <div className="actions right">
              <button type="button" className="ghost" onClick={onClose}>取消</button>
              <button type="submit" disabled={busy || !slotId}>提交补考勤</button>
            </div>
          </div>
        </form>
      </section>
    </div>
  );
}

function AttendanceModal({ client, session, headline, subline, onClose, onClosed }) {
  const [qr, setQr] = useState(null);
  const [records, setRecords] = useState([]);
  const [error, setError] = useState('');
  const [ended, setEnded] = useState(false);
  const [remaining, setRemaining] = useState(0);
  const [fullscreen, setFullscreen] = useState(false);

  const presentCount = records.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length;
  const totalCount = records.length;
  const progressPct = totalCount ? Math.round((presentCount / totalCount) * 100) : 0;

  useEffect(() => {
    if (!session || ended) return undefined;
    let cancelled = false;
    async function refresh() {
      try {
        const [nextQr, nextRecords] = await Promise.all([
          client.get(`/teacher/attendance-sessions/${session.id}/qr`),
          client.get(`/teacher/attendance-sessions/${session.id}/records`),
        ]);
        if (!cancelled) {
          setQr(nextQr);
          setRecords(nextRecords);
          setError('');
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message ?? '刷新失败');
          setEnded(true);
        }
      }
    }
    refresh();
    const timer = window.setInterval(refresh, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [session, client, ended]);

  useEffect(() => {
    if (!session || ended) return undefined;
    function tick() {
      const next = Math.max(0, Math.floor((new Date(session.endsAt ?? session.ends_at).getTime() - Date.now()) / 1000));
      setRemaining(next);
      if (next === 0) setEnded(true);
    }
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [session, ended]);

  async function closeSession() {
    if (!session) return;
    if (!window.confirm('确定提前结束考勤？二维码将立即失效，未签到学生将记为缺勤。')) return;
    await client.post(`/teacher/attendance-sessions/${session.id}/close`, {});
    setEnded(true);
    if (onClosed) await onClosed();
  }

  if (fullscreen && qr?.payload) {
    return (
      <div className="qrFullscreen">
        <div className="qrFullscreenInner">
          <div className="qrFullscreenCountdown">
            {ended ? '考勤已结束' : formatSeconds(remaining)}
          </div>
          <div className="qrQuietZone">
            <QRCodeSVG value={qr.payload} size={Math.min(520, typeof window !== 'undefined' ? window.innerWidth * 0.6 : 400)} level="M" />
          </div>
          <div className="qrFullscreenStats">
            已签到 {presentCount} / {totalCount} 人
          </div>
          <button className="qrFullscreenExit" onClick={() => setFullscreen(false)}>
            <Minimize2 size={18} />退出全屏
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="modalLayer">
      <section className="modal" role="dialog" aria-modal="true" aria-label="动态考勤">
        <div className="modalHead">
          <div>
            <h2>{headline ?? '动态考勤'}</h2>
            {subline && <p>{subline}</p>}
          </div>
          <button className="iconButton" onClick={onClose} aria-label="隐藏窗口，考勤继续" title="隐藏窗口，考勤在后台继续"><X size={18} /></button>
        </div>

        <div className="modalScroll">
          <div className="modalBody attendanceLive">
            <div className="liveHeader">
              <h3>动态签到码</h3>
              <span className={ended ? 'pill' : 'pill live'}>{ended ? '考勤已结束' : `剩余 ${formatSeconds(remaining)}`}</span>
            </div>
            <div className="qrPanel">
              <div
                className={`qrPanelTop${!ended && qr?.payload ? ' qrClickable' : ''}`}
                onClick={!ended && qr?.payload ? () => setFullscreen(true) : undefined}
                title={!ended && qr?.payload ? '点击全屏展示二维码' : undefined}
              >
                {qr?.payload && <div className="qrQuietZone qrQuietZoneSm"><QRCodeSVG value={qr.payload} size={190} /></div>}
                {!ended && qr?.payload && (
                  <div className="qrHoverOverlay">
                    <span>点击放大</span>
                  </div>
                )}
              </div>
              {qr?.expiresAt && <p className="hint">刷新时间：{new Date(qr.expiresAt).toLocaleTimeString()}</p>}
              {error && <div className="error">{error}</div>}
            </div>
            <div className="liveRight">
              <div className="liveStats">
                <div className="liveStatsText">
                  <Check size={16} />
                  <span>已签到 <strong>{presentCount}</strong> / {totalCount} 人</span>
                  <em>{progressPct}%</em>
                </div>
                <div className="progressBar"><i style={{ width: `${progressPct}%` }} /></div>
              </div>
              <div className="liveScrollArea">
                <RecordsTable records={records} ended={ended} />
              </div>
            </div>
          </div>
        </div>
        <div className="modalFooter">
          <button className="danger" disabled={ended} onClick={closeSession}>
            提前结束考勤
          </button>
        </div>
      </section>
    </div>
  );
}

function RecordsDrawer({ session, records, onClose }) {
  return (
    <div className="drawerLayer">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label={`考勤明细 ${session.id}`}>
        <div className="modalHead">
          <div>
            <h2>考勤明细</h2>
            <p>{formatDate(session.started_at)} · {methodText(session.method)}</p>
          </div>
          <button className="iconButton" onClick={onClose} aria-label="关闭明细"><X size={18} /></button>
        </div>
        <div className="modalScroll">
          <RecordsTable records={records} />
        </div>
      </aside>
    </div>
  );
}

function RecordsTable({ records, ended = true }) {
  function pillClass(status) {
    if (status === 'PRESENT' || status === 'LATE') return 'pill present';
    if (status === 'ABSENT') return ended ? 'pill danger' : 'pill neutral';
    return 'pill';
  }
  return (
    <div className="tableWrap compactTable">
      <table>
        <thead>
          <tr>
            <th>学生</th>
            <th>学号</th>
            <th>状态</th>
            <th>来源</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr key={`${record.student_id}-${record.id ?? 'absent'}`}>
              <td>{record.student_name}</td>
              <td>{record.student_no}</td>
              <td><span className={pillClass(record.status)}>{status === 'ABSENT' && !ended ? '未签到' : statusText(record.status)}</span></td>
              <td>{sourceText(record.source)}</td>
              <td>{record.checked_in_at ? new Date(record.checked_in_at).toLocaleTimeString() : '-'}</td>
            </tr>
          ))}
          {!records.length && (
            <tr>
              <td colSpan="5">暂无签到记录</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function TeacherProfile({ client, logout }) {
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ phone: '', email: '' });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    client.get('/teacher/profile').then((data) => {
      if (!cancelled) {
        setProfile(data);
        setForm({ phone: data.phone ?? '', email: data.email ?? '' });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [client]);

  async function saveProfile(event) {
    event.preventDefault();
    setError('');
    const saved = await client.put('/teacher/profile', form);
    setProfile(saved);
    setMessage('资料已保存');
  }

  async function changePassword(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    if (passwords.newPassword !== passwords.confirmPassword) {
      setError('两次输入的新密码不一致');
      return;
    }
    await client.post('/teacher/password', {
      currentPassword: passwords.currentPassword,
      newPassword: passwords.newPassword,
    });
    setPasswords({ currentPassword: '', newPassword: '', confirmPassword: '' });
    setMessage('密码已修改');
  }

  if (!profile) return <div className="panel">加载中</div>;

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>个人中心</h1>
          <p>维护联系方式和账号密码。</p>
        </div>
        <button className="ghost" onClick={logout}><LogOut size={16} />退出登录</button>
      </section>
      <section className="profileGrid">
        <form className="panel profileCard" onSubmit={saveProfile}>
          <div className="panelHead"><h2><UserRound size={17} />基本资料</h2></div>
          <label>
            工号
            <input value={profile.username ?? ''} readOnly />
          </label>
          <label>
            姓名
            <input value={profile.name ?? profile.display_name ?? ''} readOnly />
          </label>
          <label>
            院系
            <input value={profile.department ?? ''} readOnly />
          </label>
          <label>
            手机号
            <input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
          </label>
          <label>
            邮箱
            <input type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
          </label>
          <button><Save size={16} />保存资料</button>
        </form>
        <form className="panel profileCard" onSubmit={changePassword}>
          <div className="panelHead"><h2><KeyRound size={17} />修改密码</h2></div>
          <label>
            当前密码
            <input type="password" value={passwords.currentPassword} onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })} />
          </label>
          <label>
            新密码
            <input type="password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} />
          </label>
          <label>
            确认新密码
            <input type="password" value={passwords.confirmPassword} onChange={(event) => setPasswords({ ...passwords, confirmPassword: event.target.value })} />
          </label>
          <button>修改密码</button>
          {error && <div className="error">{error}</div>}
          {message && <div className="success">{message}</div>}
        </form>
      </section>
    </div>
  );
}

function StatCard({ icon, value, label }) {
  return (
    <div className="statCard">
      <span className="statIcon">{icon}</span>
      <div>
        <strong>{value}</strong>
        <p>{label}</p>
      </div>
    </div>
  );
}

function TeacherLeaveRequests({ client, onAuthExpired }) {
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [actingId, setActingId] = useState(null);
  const [toast, setToast] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    client.get(`/teacher/leave-requests?status=${statusFilter}`)
      .then((data) => {
        if (cancelled) return;
        setItems(Array.isArray(data) ? data : []);
        setError('');
      })
      .catch((err) => {
        if (cancelled) return;
        if (err.status === 401) {
          onAuthExpired();
          return;
        }
        setError(err.message ?? '加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, statusFilter, onAuthExpired]);

  async function review(id, approved) {
    setActingId(id);
    setError('');
    try {
      await client.post(`/teacher/leave-requests/${id}/review`, { approved });
      setToast(approved ? '已通过该申报' : '已驳回该申报');
      const data = await client.get(`/teacher/leave-requests?status=${statusFilter}`);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message ?? '审核失败');
    } finally {
      setActingId(null);
      setTimeout(() => setToast(''), 2400);
    }
  }

  const filters = [
    ['PENDING', '待审核'],
    ['APPROVED', '已通过'],
    ['REJECTED', '已驳回'],
    ['ALL', '全部'],
  ];

  const pendingCount = items.filter((row) => row.status === 'PENDING').length;

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>申报审核</h1>
          <p className="muted">审核学生提交的请假/特殊情况申报，通过后将自动登记为「已请假」考勤。</p>
        </div>
        <div className="summaryStrip">
          <span><ClipboardCheck size={16} />当前列表 {items.length}{statusFilter === 'PENDING' ? `（待审核 ${pendingCount}）` : ''}</span>
        </div>
      </section>

      <section className="panel">
        <div className="leaveFilterStrip" role="tablist" aria-label="状态筛选">
          {filters.map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={statusFilter === value}
              className={statusFilter === value ? 'pill live' : 'pill'}
              onClick={() => setStatusFilter(value)}
            >
              {label}
            </button>
          ))}
        </div>
        {error && <div className="error">{error}</div>}
        {toast && <div className="leaveToast" role="status">{toast}</div>}
        <div className="tableWrap">
          <table className="adminTable">
            <thead>
              <tr>
                <th>学生</th>
                <th>课程</th>
                <th>考勤时间</th>
                <th>申报原因</th>
                <th>状态</th>
                <th>审核记录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7}>加载中</td></tr>}
              {!loading && items.length === 0 && (
                <tr><td colSpan={7}>暂无申报</td></tr>
              )}
              {!loading && items.map((row) => (
                <tr key={row.id}>
                  <td>
                    <div>{row.student_name}</div>
                    <small className="muted">{row.student_no}</small>
                  </td>
                  <td>
                    <div>{row.course_name}</div>
                    <small className="muted">{row.course_code}</small>
                  </td>
                  <td>{formatDate(row.session_started_at)}</td>
                  <td>{row.reason}</td>
                  <td><span className={`statusBadge ${String(row.status).toLowerCase()}`}>{statusText(row.status)}</span></td>
                  <td>
                    {row.status === 'PENDING' ? (
                      <span className="muted">—</span>
                    ) : (
                      <div>
                        <div>{row.reviewer_name ?? '-'}</div>
                        <small className="muted">{formatDate(row.reviewed_at)}</small>
                      </div>
                    )}
                  </td>
                  <td>
                    {row.status === 'PENDING' ? (
                      <div className="actions">
                        <button
                          type="button"
                          className="ghost"
                          disabled={actingId === row.id}
                          onClick={() => review(row.id, true)}
                          aria-label={`通过 ${row.student_name} 的申报`}
                        >
                          <Check size={15} />通过
                        </button>
                        <button
                          type="button"
                          className="ghost"
                          disabled={actingId === row.id}
                          onClick={() => review(row.id, false)}
                          aria-label={`驳回 ${row.student_name} 的申报`}
                        >
                          <X size={15} />驳回
                        </button>
                      </div>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function AdminPortal({ session, view, setBreadcrumb, logout }) {
  const client = useMemo(() => api.withToken(session.token), [session.token]);
  const config = adminResources[view];
  const title = view === 'dashboard' ? '仪表盘' : view === 'courses' ? '课程管理' : view === 'departments' ? '院系管理' : config?.title ?? '后台管理';

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

function AdminDashboard({ client, session, onAuthExpired }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    client.get('/admin/dashboard')
      .then((next) => {
        if (!cancelled) setData(next);
      })
      .catch((err) => {
        if (err.status === 401) {
          onAuthExpired();
          return;
        }
        if (!cancelled) setError(err.message ?? '仪表盘加载失败');
      });
    return () => {
      cancelled = true;
    };
  }, [client, onAuthExpired]);

  if (error) return <div className="error">{error}</div>;
  if (!data) return <div className="panel">加载中</div>;

  const kpis = [
    ['学生总数', data.kpis?.studentTotal ?? 0, UsersRound, 'blue'],
    ['今日出勤', data.kpis?.todayPresent ?? 0, Check, 'green'],
    ['今日缺勤', data.kpis?.todayAbsent ?? 0, X, 'red'],
    ['今日迟到', data.kpis?.todayLate ?? 0, Clock, 'orange'],
    ['课程总数', data.kpis?.courseTotal ?? 0, BookOpen, 'blue'],
    ['院系总数', data.kpis?.departmentTotal ?? 0, Building2, 'green'],
  ];

  return (
    <div className="adminDashboard">
      <section className="welcomeBanner">
        <div>
          <h1>管理员，您好</h1>
          <p>{formatToday()} —— 这是您的今日考勤概览。</p>
        </div>
      </section>

      <section className="adminKpiGrid">
        {kpis.map(([label, value, Icon, tone]) => (
          <article className="adminKpiCard" key={label}>
            <span className={`adminKpiIcon ${tone}`}><Icon size={20} /></span>
            <div>
              <p>{label}</p>
              <strong>{value}</strong>
            </div>
          </article>
        ))}
      </section>

      <section className="dashboardCharts">
        <div className="panel">
          <div className="panelHead"><h2><BarChart3 size={17} />近 7 天出勤趋势</h2></div>
          <TrendChart rows={data.trend ?? []} />
        </div>
        <div className="panel">
          <div className="panelHead"><h2><PieChart size={17} />总体出勤状态分布</h2></div>
          <DonutSummary distribution={data.distribution ?? {}} />
        </div>
      </section>

      <section className="dashboardLower">
        <CourseAttendanceTable rows={data.courseAttendance ?? []} />
        <RecentActivities rows={data.recentActivities ?? []} />
      </section>
    </div>
  );
}

function TrendChart({ rows }) {
  const max = Math.max(1, ...rows.map((row) => Number(row.present ?? 0) + Number(row.absent ?? 0) + Number(row.late ?? 0)));
  return (
    <div className="adminTrendChart">
      {rows.map((row) => {
        const present = Number(row.present ?? 0);
        const absent = Number(row.absent ?? 0);
        const late = Number(row.late ?? 0);
        return (
          <div className="adminTrendColumn" key={row.date}>
            <div className="stackBar" style={{ height: `${Math.max(8, ((present + absent + late) / max) * 100)}%` }}>
              <i className="present" style={{ flex: present }} />
              <i className="late" style={{ flex: late }} />
              <i className="absent" style={{ flex: absent }} />
            </div>
            <span>{String(row.date ?? '').slice(5)}</span>
          </div>
        );
      })}
    </div>
  );
}

function DonutSummary({ distribution }) {
  const present = Number(distribution.present ?? 0);
  const absent = Number(distribution.absent ?? 0);
  const late = Number(distribution.late ?? 0);
  const total = Math.max(1, present + absent + late);
  const presentEnd = (present / total) * 360;
  const lateEnd = presentEnd + (late / total) * 360;
  return (
    <div className="donutSummary">
      <div
        className="donut"
        style={{ background: `conic-gradient(#16a34a 0 ${presentEnd}deg, #f59e0b ${presentEnd}deg ${lateEnd}deg, #ef4444 ${lateEnd}deg 360deg)` }}
      >
        <span>{distribution.rate ?? 0}%</span>
      </div>
      <div className="legendList">
        <span><i className="present" />出勤 {present}</span>
        <span><i className="late" />迟到 {late}</span>
        <span><i className="absent" />缺勤 {absent}</span>
      </div>
    </div>
  );
}

function CourseAttendanceTable({ rows }) {
  return (
    <section className="panel">
      <div className="panelHead"><h2>课程出勤情况</h2></div>
      <div className="courseAttendanceRows">
        {rows.map((row) => {
          const total = Number(row.total ?? 0);
          const attended = Number(row.present ?? 0) + Number(row.late ?? 0);
          const rate = total ? Math.round((attended / total) * 100) : 0;
          return (
            <div className="courseAttendanceRow" key={row.course_id ?? row.course_name}>
              <strong>{row.course_name}</strong>
              <span>{attended}/{total}</span>
              <div className="progressBar"><i style={{ width: `${rate}%` }} /></div>
              <em>{rate}%</em>
            </div>
          );
        })}
        {!rows.length && <div className="empty">暂无课程出勤数据</div>}
      </div>
    </section>
  );
}

function RecentActivities({ rows }) {
  return (
    <section className="panel">
      <div className="panelHead">
        <h2>最近活动记录</h2>
        <button className="plainLink">查看全部 →</button>
      </div>
      <div className="activityList">
        {rows.map((row) => (
          <div className="activityItem" key={row.id ?? `${row.student_name}-${row.checked_in_at}`}>
            <span className="avatarMini">{String(row.student_name ?? '学').slice(0, 1)}</span>
            <strong>{row.student_name}</strong>
            <span>{row.course_name}</span>
            <time>{formatDate(row.checked_in_at)}</time>
            <span className={`statusBadge ${String(row.status).toLowerCase()}`}>{statusText(row.status)}</span>
          </div>
        ))}
        {!rows.length && <div className="empty">暂无活动</div>}
      </div>
    </section>
  );
}

function AdminCoursesPage({ client }) {
  const [courses, setCourses] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState({ name: '', code: '', departmentId: '' });
  const [filters, setFilters] = useState({ query: '', department: '全部院系', term: '全部学期' });
  const [appliedFilters, setAppliedFilters] = useState({ query: '', department: '全部院系', term: '全部学期' });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const termOptions = useMemo(() => ['全部学期', ...Array.from(new Set(courses.map((course) => course.term).filter(Boolean)))], [courses]);
  const filteredCourses = useMemo(() => filterAdminCourses(courses, appliedFilters), [appliedFilters, courses]);
  const totalPages = Math.max(1, Math.ceil(filteredCourses.length / pageSize));
  const pageCourses = filteredCourses.slice((page - 1) * pageSize, page * pageSize);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const [nextCourses, nextDepartments] = await Promise.all([
        client.get('/admin/courses'),
        client.get('/admin/departments'),
      ]);
      setCourses(nextCourses);
      setDepartments(nextDepartments);
      if (!form.departmentId && nextDepartments[0]) {
        setForm((value) => ({ ...value, departmentId: String(nextDepartments[0].id) }));
      }
    } catch (err) {
      setError(err.message ?? '课程加载失败');
    } finally {
      setLoading(false);
    }
  }

  async function createCourse(event) {
    event.preventDefault();
    setError('');
    try {
      await client.post('/admin/courses', normalizeAdminForm(form));
      setForm({ name: '', code: '', departmentId: departments[0] ? String(departments[0].id) : '' });
      setDialogOpen(false);
      await load();
    } catch (err) {
      setError(err.message ?? '课程创建失败');
    }
  }

  async function openCourse(course) {
    setSelected(course);
    setDetail(await client.get(`/admin/courses/${course.id}`));
  }

  function applyFilters(event) {
    event.preventDefault();
    setAppliedFilters(filters);
    setPage(1);
  }

  function resetFilters() {
    const emptyFilters = { query: '', department: '全部院系', term: '全部学期' };
    setFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setPage(1);
  }

  function openCreateDialog() {
    setForm((value) => ({ ...value, departmentId: value.departmentId || (departments[0] ? String(departments[0].id) : '') }));
    setDialogOpen(true);
  }

  if (selected && detail) {
    return (
      <AdminCourseDetail
        client={client}
        detail={detail}
        departments={departments}
        onBack={() => {
          setSelected(null);
          setDetail(null);
        }}
        onChanged={async () => setDetail(await client.get(`/admin/courses/${selected.id}`))}
      />
    );
  }

  return (
    <div className="adminPage">
      <AdminPageHead title="课程管理" subtitle="检索课程并进入详情维护排课、教师和学生名单。" onRefresh={load} />
      {error && <div className="error">{error}</div>}
      <section className="panel adminCourseTablePanel">
        <CourseTableHeader
          filters={filters}
          departments={departments}
          terms={termOptions}
          total={filteredCourses.length}
          onFilters={setFilters}
          onApply={applyFilters}
          onReset={resetFilters}
          onCreate={openCreateDialog}
        />
        <div className="tableWrap">
          <table className="adminTable courseDataTable">
            <thead>
              <tr>
                <th>序号</th>
                <th>课程名称</th>
                <th>课程代码</th>
                <th>院系</th>
                <th>授课教师</th>
                <th>学期</th>
                <th>选课人数</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan="10">加载中</td></tr>}
              {!loading && pageCourses.map((course, index) => (
                <tr key={course.id}>
                  <td>{(page - 1) * pageSize + index + 1}</td>
                  <td><strong>{course.name}</strong></td>
                  <td>{course.code}</td>
                  <td>{course.department_name ?? '未设置院系'}</td>
                  <td>{course.teacher_name ?? '未分配'}</td>
                  <td>{course.term ?? '未设置'}</td>                 
                  <td>{Number(course.student_count ?? 0)} 人</td>
                  <td>
                    <button className="ghost" aria-label={`查看课程 ${course.name}`} onClick={() => openCourse(course)}>查看详情</button>
                  </td>
                </tr>
              ))}
              {!loading && !pageCourses.length && <tr><td colSpan="10">暂无课程</td></tr>}
            </tbody>
          </table>
        </div>
        <AdminPagination
          total={filteredCourses.length}
          page={page}
          totalPages={totalPages}
          pageSize={pageSize}
          onPage={setPage}
          onPageSize={(nextSize) => {
            setPageSize(nextSize);
            setPage(1);
          }}
        />
      </section>
      {dialogOpen && (
        <CourseFormDialog
          form={form}
          departments={departments}
          onForm={setForm}
          onSubmit={createCourse}
          onClose={() => setDialogOpen(false)}
        />
      )}
    </div>
  );
}

function CourseTableHeader({ filters, departments, terms, total, onFilters, onApply, onReset, onCreate }) {
  return (
    <div className="studentTableHeader courseTableHeader">
      <form className="studentSearchBar courseSearchBar" onSubmit={onApply}>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="搜索课程名称或代码"
              value={filters.query}
              onChange={(event) => onFilters({ ...filters, query: event.target.value })}
            />
          </span>
        </label>
        <label>
          院系
          <select value={filters.department} onChange={(event) => onFilters({ ...filters, department: event.target.value })}>
            <option>全部院系</option>
            {departments.map((department) => <option key={department.id}>{department.name}</option>)}
          </select>
        </label>
        <label>
          学期
          <select value={filters.term} onChange={(event) => onFilters({ ...filters, term: event.target.value })}>
            {terms.map((term) => <option key={term}>{term}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="submit"><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </form>
      <div className="studentTableActions">
        <strong>共搜索到 {total} 门课程</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />创建课程</button>
      </div>
    </div>
  );
}

function CourseFormDialog({ form, departments, onForm, onSubmit, onClose }) {
  return (
    <div className="modalLayer">
      <section className="modal adminResourceDialog" role="dialog" aria-modal="true" aria-label="创建课程">
        <div className="modalHead">
          <div>
            <h2>创建课程</h2>
            <p>创建基础课程后，可进入详情维护教师、排课和学生名单。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label="关闭创建课程"><X size={18} /></button>
        </div>
        <form className="modalBody adminResourceForm" onSubmit={onSubmit}>
          <label>
            <span className="requiredLabel"><i>*</i>课程名称</span>
            <input
              placeholder="请输入课程名称"
              required
              value={form.name}
              onChange={(event) => onForm({ ...form, name: event.target.value })}
            />
          </label>
          <label>
            <span className="requiredLabel"><i>*</i>课程代码</span>
            <input
              placeholder="请输入课程代码，如：CS101"
              required
              value={form.code}
              onChange={(event) => onForm({ ...form, code: event.target.value })}
            />
          </label>
          <label className="fullWidthField">
            <span className="requiredLabel"><i>*</i>所属院系</span>
            <select
              required
              value={form.departmentId}
              onChange={(event) => onForm({ ...form, departmentId: event.target.value })}
            >
              <option value="">请选择所属院系</option>
              {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
            </select>
          </label>
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit">确定</button>
          </div>
        </form>
      </section>
    </div>
  );
}

function AdminCourseDetail({ client, detail, onBack, onChanged }) {
  const course = detail.course ?? {};
  const hasSavedTeacher = Boolean(detail.teacher?.teacher_id ?? detail.teacher?.id);
  const [teacher, setTeacher] = useState({
    teacherId: detail.teacher?.teacher_id ?? detail.teacher?.id ?? '',
    term: detail.teacher?.term ?? '',
  });
  const [teachers, setTeachers] = useState([]);
  const [classrooms, setClassrooms] = useState([]);
  const [students, setStudents] = useState([]);
  const [terms, setTerms] = useState([]);
  const [teacherSearch, setTeacherSearch] = useState('');
  const [studentSearch, setStudentSearch] = useState('');
  const [selectedStudentIds, setSelectedStudentIds] = useState([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [studentDialogOpen, setStudentDialogOpen] = useState(false);
  const [slotEditor, setSlotEditor] = useState(null);

  useEffect(() => {
    setTeacher({
      teacherId: detail.teacher?.teacher_id ?? detail.teacher?.id ?? '',
      term: detail.teacher?.term ?? '',
    });
    setSelectedStudentIds([]);
    setStudentSearch('');
    setStudentDialogOpen(false);
    setSlotEditor(null);
  }, [detail]);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      client.get('/admin/teachers'),
      client.get('/admin/students'),
      client.get('/admin/terms'),
      client.get('/admin/classrooms'),
    ])
      .then(([teachersResult, studentsResult, termsResult, classroomsResult]) => {
        if (cancelled) return;
        if (teachersResult.status === 'fulfilled') setTeachers(teachersResult.value);
        if (studentsResult.status === 'fulfilled') setStudents(studentsResult.value);
        setTerms(termsResult.status === 'fulfilled' ? termsResult.value : fallbackTerms);
        if (classroomsResult.status === 'fulfilled') setClassrooms(classroomsResult.value);
      });
    return () => {
      cancelled = true;
    };
  }, [client]);

  const termOptions = useMemo(() => {
    const values = new Map(terms.map((item) => [item.value, item.label ?? item.value]));
    if (teacher.term && !values.has(teacher.term)) values.set(teacher.term, teacher.term);
    return Array.from(values.entries()).map(([value, label]) => ({ value, label }));
  }, [teacher.term, terms]);

  const teacherOptions = useMemo(() => {
    const currentId = detail.teacher?.teacher_id ?? detail.teacher?.id;
    if (!currentId || teachers.some((item) => String(item.id) === String(currentId))) return teachers;
    return [...teachers, { ...detail.teacher, id: currentId }];
  }, [detail.teacher, teachers]);

  const assignedTeachers = useMemo(() => {
    const courseTeachers = detail.teachers?.length ? detail.teachers : (detail.teacher?.id || detail.teacher?.teacher_id ? [detail.teacher] : []);
    return courseTeachers.map((item) => ({ ...item, id: item.teacher_id ?? item.id }));
  }, [detail.teacher, detail.teachers]);

  const scheduleSlots = detail.scheduleSlots ?? [];
  const slotsByKey = useMemo(() => {
    const map = new Map();
    for (const slot of scheduleSlots) map.set(`${slot.weekday}-${slot.period}`, slot);
    return map;
  }, [scheduleSlots]);

  const enrolledStudentIds = useMemo(() => new Set((detail.students ?? []).map((student) => Number(student.id))), [detail.students]);
  const filteredStudents = useMemo(() => {
    const query = studentSearch.trim().toLowerCase();
    return students
      .filter((student) => !enrolledStudentIds.has(Number(student.id)))
      .filter((student) => {
        if (!query) return true;
        return [student.name, student.username, student.student_no, student.department_name, String(student.id)]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query));
      });
  }, [enrolledStudentIds, studentSearch, students]);

  const allFilteredSelected = filteredStudents.length > 0 &&
    filteredStudents.every(s => selectedStudentIds.includes(String(s.id)));
  const someFilteredSelected = filteredStudents.some(s => selectedStudentIds.includes(String(s.id)));

  function toggleAllFiltered() {
    if (allFilteredSelected) {
      const filteredIds = new Set(filteredStudents.map(s => String(s.id)));
      setSelectedStudentIds(current => current.filter(id => !filteredIds.has(id)));
    } else {
      const filteredIds = filteredStudents.map(s => String(s.id));
      setSelectedStudentIds(current => [...new Set([...current, ...filteredIds])]);
    }
  }

  async function saveTeacher(event) {
    event.preventDefault();
    setError('');
    try {
      await client.put(`/admin/courses/${course.id}/teacher`, normalizeAdminForm(teacher));
      setMessage('授课教师已保存');
      await onChanged();
    } catch (err) {
      setError(err.message ?? '授课教师保存失败');
    }
  }

  async function addSelectedStudents(event) {
    event.preventDefault();
    if (!selectedStudentIds.length) return;
    setError('');
    try {
      for (const id of selectedStudentIds) {
        await client.post(`/admin/courses/${course.id}/students`, { studentId: Number(id) });
      }
      setSelectedStudentIds([]);
      setStudentSearch('');
      setStudentDialogOpen(false);
      setMessage('学生已加入课程');
      await onChanged();
    } catch (err) {
      setError(err.message ?? '学生添加失败');
    }
  }

  async function removeStudent(student) {
    setError('');
    try {
      await client.delete(`/admin/courses/${course.id}/students/${student.id}`);
      setMessage(`${student.name} 已移出课程`);
      await onChanged();
    } catch (err) {
      setError(err.message ?? '学生移除失败');
    }
  }

  function toggleStudent(id) {
    setSelectedStudentIds((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  }

  function openSlotEditor(weekday, period, slot) {
    const defaultTeacherId = slot?.teacher_id ?? teacher.teacherId ?? assignedTeachers[0]?.id ?? teacherOptions[0]?.id ?? '';
    const defaultClassroomId = slot?.classroom_id ?? classrooms[0]?.id ?? '';
    setError('');
    setSlotEditor({
      id: slot?.id,
      weekday,
      period,
      teacherId: defaultTeacherId ? String(defaultTeacherId) : '',
      classroomId: defaultClassroomId ? String(defaultClassroomId) : '',
      courseType: slot?.course_type ?? 'LECTURE',
      error: '',
    });
  }

  async function saveSlot(event) {
    event.preventDefault();
    if (!slotEditor) return;
    const payload = {
      ...(slotEditor.id ? { id: slotEditor.id } : {}),
      weekday: slotEditor.weekday,
      period: slotEditor.period,
      teacherId: Number(slotEditor.teacherId),
      classroomId: Number(slotEditor.classroomId),
      courseType: slotEditor.courseType,
    };
    try {
      await client.put(`/admin/courses/${course.id}/schedule-slots`, payload);
      setSlotEditor(null);
      setMessage('排课已保存');
      await onChanged();
    } catch (err) {
      setSlotEditor((current) => current ? { ...current, error: err.message ?? '排课保存失败' } : current);
    }
  }

  async function deleteSlot() {
    if (!slotEditor?.id) return;
    try {
      await client.delete(`/admin/courses/${course.id}/schedule-slots/${slotEditor.id}`);
      setSlotEditor(null);
      setMessage('排课已删除');
      await onChanged();
    } catch (err) {
      setSlotEditor((current) => current ? { ...current, error: err.message ?? '排课删除失败' } : current);
    }
  }

  return (
    <div className="adminPage courseManagementDetail">
      <section className="pageHead detailHead">
        <button className="iconButton" onClick={onBack} aria-label="返回课程列表"><ChevronLeft size={18} /></button>
        <div>
          <h1>{course.name}</h1>
          <p>{course.code} · {course.department_name ?? '未设置院系'}</p>
        </div>
      </section>
      {message && <div className="success">{message}</div>}
      {error && <div className="error">{error}</div>}
      <section className="courseDetailStats">
        <div className="statCard compactStat">
          <span className="eyebrow">授课教师</span>
          <strong>{assignedTeachers.length || (hasSavedTeacher ? 1 : 0)}</strong>
        </div>
        <div className="statCard compactStat">
          <span className="eyebrow">每周课时</span>
          <strong>{scheduleSlots.length}</strong>
        </div>
        <div className="statCard compactStat">
          <span className="eyebrow">选课学生</span>
          <strong>{detail.students?.length ?? 0}</strong>
        </div>
      </section>
      <section className="panel courseSetupPanel">
        <div className="panelHead"><h2><CalendarDays size={17} />课程安排</h2></div>
        <form className="courseDetailSection" onSubmit={saveTeacher}>
          <div>
            <h3>授课安排</h3>
          </div>
          <div className="courseDetailFields">
            <TeacherCombobox
              teachers={teacherOptions}
              selectedId={teacher.teacherId}
              query={teacherSearch}
              onQuery={setTeacherSearch}
              onSelect={(teacherId) => setTeacher({ ...teacher, teacherId })}
            />
            <label>
              学期
              <select value={teacher.term} onChange={(event) => setTeacher({ ...teacher, term: event.target.value })}>
                <option value="">请选择学期</option>
                {termOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
              </select>
            </label>
          </div>
          <div className="formActions"><button><Save size={16} />保存教师</button></div>
        </form>
        <section className="courseDetailSection">
          <div>
            <h3>排课管理</h3>
          </div>
          <div className="scheduleGrid" aria-label="排课网格">
            <div className="scheduleCorner" />
            {scheduleWeekDays.map((weekday) => <div key={weekday} className="scheduleHead">{weekday}</div>)}
            {schedulePeriods.map(({ period, time }) => (
              <React.Fragment key={period}>
                <div className="schedulePeriod">
                  <strong>{period}</strong>
                  <span>{time.split('\n').map((line) => <React.Fragment key={line}>{line}<br /></React.Fragment>)}</span>
                </div>
                {scheduleWeekDays.map((weekday) => {
                  const slot = slotsByKey.get(`${weekday}-${period}`);
                  const typeText = slot?.course_type === 'LAB' ? '实验' : '讲课';
                  const label = slot
                    ? `编辑排课 ${weekday} 第${period}节 ${slot.teacher_name} ${slot.classroom_name} ${typeText}`
                    : `排课 ${weekday} 第${period}节`;
                  return (
                    <button
                      type="button"
                      key={`${weekday}-${period}`}
                      className={slot ? `scheduleSlot filled ${slot.course_type === 'LAB' ? 'labSlot' : 'lectureSlot'}` : 'scheduleSlot emptySlot'}
                      aria-label={label}
                      onClick={() => openSlotEditor(weekday, period, slot)}
                    >
                      {slot ? (
                        <>
                          <strong>{typeText}</strong>
                          <span>{slot.teacher_name}</span>
                          <em>{slot.classroom_name}</em>
                        </>
                      ) : <Plus size={16} />}
                    </button>
                  );
                })}
              </React.Fragment>
            ))}
          </div>
        </section>
      </section>
      <section className="panel">
        <div className="panelHead rosterHead">
          <h2>选课学生名单</h2>
          <div className="rosterActions">
            {!hasSavedTeacher && <span className="muted">请先保存授课安排后添加学生</span>}
            <button type="button" disabled={!hasSavedTeacher} onClick={() => setStudentDialogOpen(true)}>
              <Plus size={16} />添加学生
            </button>
          </div>
        </div>
        <div className="tableWrap">
          <table>
            <thead><tr><th>姓名</th><th>学号</th><th>院系</th><th>操作</th></tr></thead>
            <tbody>
              {(detail.students ?? []).map((student) => (
                <tr key={student.id}>
                  <td>{student.name}</td>
                  <td>{student.student_no}</td>
                  <td>{student.department_name}</td>
                  <td>
                    <button type="button" className="ghost dangerButton" aria-label={`移除 ${student.name}`} onClick={() => removeStudent(student)}>
                      <X size={15} />移除
                    </button>
                  </td>
                </tr>
              ))}
              {!(detail.students ?? []).length && <tr><td colSpan="4">暂无学生</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
      {studentDialogOpen && (
        <StudentPickerDialog
          students={filteredStudents}
          search={studentSearch}
          selectedStudentIds={selectedStudentIds}
          allSelected={allFilteredSelected}
          someSelected={someFilteredSelected}
          onSearch={setStudentSearch}
          onToggleStudent={toggleStudent}
          onToggleAll={toggleAllFiltered}
          onClose={() => {
            setStudentDialogOpen(false);
            setSelectedStudentIds([]);
            setStudentSearch('');
          }}
          onConfirm={addSelectedStudents}
        />
      )}
      {slotEditor && (
        <ScheduleSlotDialog
          slot={slotEditor}
          teachers={teacherOptions}
          classrooms={classrooms}
          onSlot={setSlotEditor}
          onSubmit={saveSlot}
          onDelete={deleteSlot}
          onClose={() => setSlotEditor(null)}
        />
      )}
    </div>
  );
}

function ScheduleSlotDialog({ slot, teachers, classrooms, onSlot, onSubmit, onDelete, onClose }) {
  const [teacherQuery, setTeacherQuery] = useState('');
  return (
    <div className="modalLayer">
      <section className="modal scheduleSlotDialog" role="dialog" aria-modal="true" aria-label="编辑排课">
        <div className="modalHead">
          <div>
            <h2>编辑排课</h2>
            <p>{slot.weekday} · 第 {slot.period} 节</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label="关闭排课编辑"><X size={18} /></button>
        </div>
        <form className="modalBody" onSubmit={onSubmit}>
          {slot.error && <div className="error">{slot.error}</div>}
          <TeacherCombobox
            teachers={teachers}
            selectedId={slot.teacherId}
            query={teacherQuery}
            onQuery={setTeacherQuery}
            onSelect={(teacherId) => onSlot((current) => ({ ...current, teacherId }))}
          />
          <label>
            教室
            <select value={slot.classroomId} onChange={(event) => onSlot((current) => ({ ...current, classroomId: event.target.value }))}>
              <option value="">请选择教室</option>
              {classrooms.map((classroom) => <option key={classroom.id} value={classroom.id}>{classroom.name}</option>)}
            </select>
          </label>
          <fieldset className="segmentedField">
            <legend>课程类型</legend>
            <label>
              <input
                type="radio"
                name="courseType"
                checked={slot.courseType === 'LECTURE'}
                onChange={() => onSlot((current) => ({ ...current, courseType: 'LECTURE' }))}
              />
              <span>讲课</span>
            </label>
            <label>
              <input
                type="radio"
                name="courseType"
                checked={slot.courseType === 'LAB'}
                onChange={() => onSlot((current) => ({ ...current, courseType: 'LAB' }))}
              />
              <span>实验</span>
            </label>
          </fieldset>
          <div className="formActions dialogActions">
            {slot.id && <button type="button" className="ghost dangerButton" onClick={onDelete}>删除排课</button>}
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit" disabled={!slot.teacherId || !slot.classroomId}><Save size={16} />保存排课</button>
          </div>
        </form>
      </section>
    </div>
  );
}

function TeacherCombobox({ teachers, selectedId, query, onQuery, onSelect }) {
  const [open, setOpen] = useState(false);
  const selectedTeacher = teachers.find((teacher) => String(teacher.id) === String(selectedId));
  const selectedLabel = selectedTeacher ? teacherOptionText(selectedTeacher) : '';
  const filteredTeachers = useMemo(() => {
    const term = query.trim().toLowerCase();
    return teachers.filter((teacher) => {
      if (!term) return true;
      return [teacher.name, teacher.username, teacher.department_name, teacher.department, String(teacher.id)]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
    });
  }, [query, teachers]);

  return (
    <label className="comboField">
      授课教师
      <div className="comboBox">
        <input
          role="combobox"
          aria-expanded={open}
          aria-controls="teacher-options"
          value={open ? query : selectedLabel}
          placeholder="输入教师姓名、账号或院系"
          onFocus={() => {
            setOpen(true);
            onQuery('');
          }}
          onChange={(event) => {
            setOpen(true);
            onQuery(event.target.value);
          }}
        />
        {open && (
          <div className="comboMenu" id="teacher-options" role="listbox">
            {filteredTeachers.map((teacher) => (
              <button
                type="button"
                className="comboOption"
                key={teacher.id}
                role="option"
                aria-selected={String(teacher.id) === String(selectedId)}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  onSelect(String(teacher.id));
                  onQuery('');
                  setOpen(false);
                }}
              >
                {teacherOptionText(teacher)}
              </button>
            ))}
            {!filteredTeachers.length && <span className="comboEmpty">暂无匹配教师</span>}
          </div>
        )}
      </div>
    </label>
  );
}

function StudentPickerDialog({ students, search, selectedStudentIds, allSelected, someSelected, onSearch, onToggleStudent, onToggleAll, onClose, onConfirm }) {
  return (
    <div className="modalLayer">
      <section className="modal studentPickerDialog" role="dialog" aria-modal="true" aria-label="添加选课学生">
        <div className="modalHead">
          <div>
            <h2>添加选课学生</h2>
            <p>搜索并勾选需要加入课程的学生。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label="关闭添加学生"><X size={18} /></button>
        </div>
        <form className="modalBody" onSubmit={onConfirm}>
          <label className="searchField">
            搜索学生
            <span>
              <Search size={16} />
              <input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="搜索姓名、学号、账号或院系" />
            </span>
          </label>
          <div className="tableWrap studentPickerTable">
            <table>
              <thead><tr><th>
                        <input
                          type="checkbox"
                          aria-label="全选学生"
                          checked={allSelected}
                          ref={el => { if (el) el.indeterminate = someSelected && !allSelected; }}
                          onChange={onToggleAll}
                        />
                      </th><th>姓名</th><th>学号</th><th>院系</th></tr></thead>
              <tbody>
                {students.map((student) => {
                  const id = String(student.id);
                  return (
                    <tr key={student.id}>
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`${student.name} ${student.student_no}`}
                          checked={selectedStudentIds.includes(id)}
                          onChange={() => onToggleStudent(id)}
                        />
                      </td>
                      <td>{student.name}</td>
                      <td>{student.student_no}</td>
                      <td>{student.department_name}</td>
                    </tr>
                  );
                })}
                {!students.length && <tr><td colSpan="4">暂无可添加学生</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button disabled={!selectedStudentIds.length}><Plus size={16} />确认添加</button>
          </div>
        </form>
      </section>
    </div>
  );
}

function AdminResourcePage({ client, config }) {
  const isStudentResource = config.title === '学生管理';
  const isTeacherResource = config.title === '教师管理';
  const isClassroomResource = config.title === '教室管理';
  const [items, setItems] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState(() => emptyAdminForm(config.fields));
  const [query, setQuery] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('全部院系');
  const [gradeFilter, setGradeFilter] = useState('全部年级');
  const [studentDraftFilters, setStudentDraftFilters] = useState({ query: '', department: '全部院系', grade: '全部年级' });
  const [studentAppliedFilters, setStudentAppliedFilters] = useState({ query: '', department: '全部院系', grade: '全部年级' });
  const [teacherDraftFilters, setTeacherDraftFilters] = useState({ query: '', department: '全部院系' });
  const [teacherAppliedFilters, setTeacherAppliedFilters] = useState({ query: '', department: '全部院系' });
  const [studentDialogOpen, setStudentDialogOpen] = useState(false);
  const [teacherDialogOpen, setTeacherDialogOpen] = useState(false);
  const [classroomDialogOpen, setClassroomDialogOpen] = useState(false);
  const [studentPage, setStudentPage] = useState(1);
  const [studentPageSize, setStudentPageSize] = useState(10);
  const [teacherPage, setTeacherPage] = useState(1);
  const [teacherPageSize, setTeacherPageSize] = useState(10);
  const [classroomPage, setClassroomPage] = useState(1);
  const [classroomPageSize, setClassroomPageSize] = useState(10);
  const [classroomDraftFilters, setClassroomDraftFilters] = useState({ query: '', building: '全部教学楼' });
  const [classroomAppliedFilters, setClassroomAppliedFilters] = useState({ query: '', building: '全部教学楼' });
  const [editId, setEditId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const grades = ['全部年级', ...Array.from(new Set(items.map((item) => item.grade).filter(Boolean)))];
  const classroomBuildings = ['教一', '教二', '教三', '教四'];
  const filteredItems = useMemo(() => {
    if (isStudentResource) {
      return filterStudentRows(items, studentAppliedFilters.query).filter((item) => {
        const inDepartment = studentAppliedFilters.department === '全部院系' || item.department_name === studentAppliedFilters.department;
        const inGrade = studentAppliedFilters.grade === '全部年级' || item.grade === studentAppliedFilters.grade;
        return inDepartment && inGrade;
      });
    }
    if (isTeacherResource) {
      return filterTeacherRows(items, teacherAppliedFilters.query).filter((item) => {
        const inDepartment = teacherAppliedFilters.department === '全部院系' || item.department_name === teacherAppliedFilters.department;
        return inDepartment;
      });
    }
    if (isClassroomResource) {
      return filterClassroomRows(items, classroomAppliedFilters.query).filter((item) => {
        const inBuilding = classroomAppliedFilters.building === '全部教学楼' || item.building === classroomAppliedFilters.building;
        return inBuilding;
      });
    }
    return filterRows(items, query).filter((item) => {
      const inDepartment = departmentFilter === '全部院系' || item.department_name === departmentFilter;
      return inDepartment;
    });
  }, [departmentFilter, isStudentResource, isTeacherResource, isClassroomResource, items, query, studentAppliedFilters, teacherAppliedFilters, classroomAppliedFilters]);
  const studentTotalPages = Math.max(1, Math.ceil(filteredItems.length / studentPageSize));
  const teacherTotalPages = Math.max(1, Math.ceil(filteredItems.length / teacherPageSize));
  const classroomTotalPages = Math.max(1, Math.ceil(filteredItems.length / classroomPageSize));
  const studentRows = isStudentResource
    ? filteredItems.slice((studentPage - 1) * studentPageSize, studentPage * studentPageSize)
    : filteredItems;
  const displayedStudentRows = isStudentResource
    ? studentRows.map((row, index) => ({ ...row, display_index: (studentPage - 1) * studentPageSize + index + 1 }))
    : studentRows;
  const teacherRows = isTeacherResource
    ? filteredItems.slice((teacherPage - 1) * teacherPageSize, teacherPage * teacherPageSize)
    : filteredItems;
  const displayedTeacherRows = isTeacherResource
    ? teacherRows.map((row, index) => ({ ...row, display_index: (teacherPage - 1) * teacherPageSize + index + 1 }))
    : teacherRows;
  const classroomRows = isClassroomResource
    ? filteredItems.slice((classroomPage - 1) * classroomPageSize, classroomPage * classroomPageSize)
    : filteredItems;
  const displayedClassroomRows = isClassroomResource
    ? classroomRows.map((row, index) => ({ ...row, display_index: (classroomPage - 1) * classroomPageSize + index + 1 }))
    : classroomRows;

  useEffect(() => {
    setForm(emptyAdminForm(config.fields));
    setEditId(null);
    setQuery('');
    setDepartmentFilter('全部院系');
    setGradeFilter('全部年级');
    setStudentDraftFilters({ query: '', department: '全部院系', grade: '全部年级' });
    setStudentAppliedFilters({ query: '', department: '全部院系', grade: '全部年级' });
    setTeacherDraftFilters({ query: '', department: '全部院系' });
    setTeacherAppliedFilters({ query: '', department: '全部院系' });
    setStudentDialogOpen(false);
    setTeacherDialogOpen(false);
    setClassroomDialogOpen(false);
    setStudentPage(1);
    setTeacherPage(1);
    setClassroomPage(1);
    setClassroomDraftFilters({ query: '', building: '全部教学楼' });
    setClassroomAppliedFilters({ query: '', building: '全部教学楼' });
    setMessage('');
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, config]);

  useEffect(() => {
    setStudentPage((current) => Math.min(current, studentTotalPages));
  }, [studentTotalPages]);

  useEffect(() => {
    setClassroomPage((current) => Math.min(current, classroomTotalPages));
  }, [classroomTotalPages]);

  useEffect(() => {
    setTeacherPage((current) => Math.min(current, teacherTotalPages));
  }, [teacherTotalPages]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const [nextItems, nextDepartments] = await Promise.all([
        client.get(config.endpoint),
        client.get('/admin/departments'),
      ]);
      setItems(nextItems);
      setDepartments(nextDepartments);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    try {
      if (editId && !config.noEdit) {
        await client.put(`${config.endpoint}/${editId}`, normalizeAdminForm(form));
      } else {
        await client.post(config.endpoint, normalizeAdminForm(form));
      }
      setEditId(null);
      setForm(emptyAdminForm(config.fields));
      setStudentDialogOpen(false);
      setTeacherDialogOpen(false);
      setClassroomDialogOpen(false);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function remove(id) {
    setError('');
    setMessage('');
    try {
      await client.delete(`${config.endpoint}/${id}`);
      if (editId === id) {
        setEditId(null);
        setForm(emptyAdminForm(config.fields));
      }
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  function edit(row) {
    setEditId(row.id);
    setForm(Object.fromEntries(config.fields.map(([name]) => [name, adminFieldValue(row, name)])));
    if (isStudentResource) setStudentDialogOpen(true);
    if (isTeacherResource) setTeacherDialogOpen(true);
    if (isClassroomResource) setClassroomDialogOpen(true);
  }

  function openCreateStudent() {
    setEditId(null);
    setForm(emptyAdminForm(config.fields));
    setStudentDialogOpen(true);
  }

  function openCreateTeacher() {
    setEditId(null);
    setForm(emptyAdminForm(config.fields));
    setTeacherDialogOpen(true);
  }

  function closeStudentDialog() {
    setStudentDialogOpen(false);
    setEditId(null);
    setForm(emptyAdminForm(config.fields));
  }

  function closeTeacherDialog() {
    setTeacherDialogOpen(false);
    setEditId(null);
    setForm(emptyAdminForm(config.fields));
  }

  function applyStudentFilters() {
    setStudentAppliedFilters(studentDraftFilters);
    setStudentPage(1);
  }

  function resetStudentFilters() {
    const emptyFilters = { query: '', department: '全部院系', grade: '全部年级' };
    setStudentDraftFilters(emptyFilters);
    setStudentAppliedFilters(emptyFilters);
    setStudentPage(1);
  }

  function applyTeacherFilters() {
    setTeacherAppliedFilters(teacherDraftFilters);
    setTeacherPage(1);
  }

  function resetTeacherFilters() {
    const emptyFilters = { query: '', department: '全部院系' };
    setTeacherDraftFilters(emptyFilters);
    setTeacherAppliedFilters(emptyFilters);
    setTeacherPage(1);
  }

  function openCreateClassroom() {
    setEditId(null);
    setForm(emptyAdminForm(config.fields));
    setClassroomDialogOpen(true);
  }

  function closeClassroomDialog() {
    setClassroomDialogOpen(false);
    setEditId(null);
    setForm(emptyAdminForm(config.fields));
  }

  function applyClassroomFilters() {
    setClassroomAppliedFilters(classroomDraftFilters);
    setClassroomPage(1);
  }

  function resetClassroomFilters() {
    const emptyFilters = { query: '', building: '全部教学楼' };
    setClassroomDraftFilters(emptyFilters);
    setClassroomAppliedFilters(emptyFilters);
    setClassroomPage(1);
  }

  async function resetPassword(row) {
    setError('');
    setMessage('');
    if (!window.confirm(`确认将「${row.name}」的密码重置为 123456？`)) return;
    try {
      await client.post(`${config.endpoint}/${row.id}/reset-password`, {});
      setMessage(`${row.name}的密码已重置为 123456`);
    } catch (err) {
      setError(err.message);
    }
  }

  if (isStudentResource) {
    const isEditingStudent = editId && !config.noEdit;
    const studentDialogFields = isEditingStudent
      ? config.fields.filter(([name]) => name !== 'username' && name !== 'password')
      : config.fields;
    return (
      <div className="adminPage">
        <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" onRefresh={load} />
        {error && <div className="error">{error}</div>}
        {message && <div className="success">{message}</div>}
        <AdminDataTable
          rows={displayedStudentRows}
          columns={config.columns}
          loading={loading}
          resourceTitle={config.title.replace(/管理$/, '')}
          onEdit={config.noEdit ? null : edit}
          onDelete={remove}
          onResetPassword={resetPassword}
          header={(
            <StudentTableHeader
              filters={studentDraftFilters}
              onFilters={setStudentDraftFilters}
              departments={departments}
              grades={grades}
              total={filteredItems.length}
              onApply={applyStudentFilters}
              onReset={resetStudentFilters}
              onCreate={openCreateStudent}
            />
          )}
          footer={(
            <AdminPagination
              total={filteredItems.length}
              page={studentPage}
              totalPages={studentTotalPages}
              pageSize={studentPageSize}
              onPage={setStudentPage}
              onPageSize={(nextSize) => {
                setStudentPageSize(nextSize);
                setStudentPage(1);
              }}
            />
          )}
        />
        {studentDialogOpen && (
          <AdminResourceFormDialog
            title={isEditingStudent ? '编辑学生' : '新增学生'}
            fields={studentDialogFields}
            form={form}
            departments={departments}
            submitLabel={isEditingStudent ? '保存' : '新增'}
            onForm={setForm}
            onSubmit={submit}
            onClose={closeStudentDialog}
          />
        )}
      </div>
    );
  }

  if (isTeacherResource) {
    return (
      <div className="adminPage">
        <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" onRefresh={load} />
        {error && <div className="error">{error}</div>}
        {message && <div className="success">{message}</div>}
        <AdminDataTable
          rows={displayedTeacherRows}
          columns={config.columns}
          loading={loading}
          resourceTitle={config.title.replace(/管理$/, '')}
          onEdit={config.noEdit ? null : edit}
          onDelete={remove}
          onResetPassword={resetPassword}
          header={(
            <TeacherTableHeader
              filters={teacherDraftFilters}
              onFilters={setTeacherDraftFilters}
              departments={departments}
              total={filteredItems.length}
              onApply={applyTeacherFilters}
              onReset={resetTeacherFilters}
              onCreate={openCreateTeacher}
            />
          )}
          footer={(
            <AdminPagination
              total={filteredItems.length}
              page={teacherPage}
              totalPages={teacherTotalPages}
              pageSize={teacherPageSize}
              onPage={setTeacherPage}
              onPageSize={(nextSize) => {
                setTeacherPageSize(nextSize);
                setTeacherPage(1);
              }}
            />
          )}
        />
        {teacherDialogOpen && (
          <AdminResourceFormDialog
            title={editId && !config.noEdit ? '编辑教师' : '新增教师'}
            fields={editId && !config.noEdit ? config.fields.filter(([name]) => name !== 'username' && name !== 'password') : config.fields}
            form={form}
            departments={departments}
            submitLabel={editId && !config.noEdit ? '保存' : '新增'}
            onForm={setForm}
            onSubmit={submit}
            onClose={closeTeacherDialog}
          />
        )}
      </div>
    );
  }

  if (isClassroomResource) {
    return (
      <div className="adminPage">
        <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" onRefresh={load} />
        {error && <div className="error">{error}</div>}
        <AdminDataTable
          rows={displayedClassroomRows}
          columns={config.columns}
          labels={config.labels}
          loading={loading}
          resourceTitle={config.title.replace(/管理$/, '')}
          onEdit={config.noEdit ? null : edit}
          onDelete={remove}
          header={(
            <ClassroomTableHeader
              filters={classroomDraftFilters}
              onFilters={setClassroomDraftFilters}
              buildings={classroomBuildings}
              total={filteredItems.length}
              onApply={applyClassroomFilters}
              onReset={resetClassroomFilters}
              onCreate={openCreateClassroom}
            />
          )}
          footer={(
            <AdminPagination
              total={filteredItems.length}
              page={classroomPage}
              totalPages={classroomTotalPages}
              pageSize={classroomPageSize}
              onPage={setClassroomPage}
              onPageSize={(nextSize) => {
                setClassroomPageSize(nextSize);
                setClassroomPage(1);
              }}
            />
          )}
        />
        {classroomDialogOpen && (
          <ClassroomFormDialog
            title={editId && !config.noEdit ? '编辑教室' : '新增教室'}
            form={form}
            buildings={classroomBuildings}
            isEdit={Boolean(editId && !config.noEdit)}
            onForm={setForm}
            onSubmit={submit}
            onClose={closeClassroomDialog}
          />
        )}
      </div>
    );
  }

  return (
    <div className="adminPage">
      <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" onRefresh={load} />
      <section className="panel adminEditor">
        <form className="inlineForm" onSubmit={submit}>
          {config.fields.map(([name, label]) => (
            <label key={name}>
              {label}
              {name === 'departmentId' ? (
                <select value={form[name] ?? ''} onChange={(event) => setForm({ ...form, [name]: event.target.value })}>
                  <option value="">请选择院系</option>
                  {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                </select>
              ) : (
                <input
                  type="text"
                  value={form[name] ?? ''}
                  onChange={(event) => setForm({ ...form, [name]: event.target.value })}
                />
              )}
            </label>
          ))}
          <button type="submit">{editId && !config.noEdit ? <Save size={16} /> : <Plus size={16} />}{editId && !config.noEdit ? '保存' : '新增'}</button>
          {editId && !config.noEdit && <button type="button" className="ghost" onClick={() => { setEditId(null); setForm(emptyAdminForm(config.fields)); }}><X size={16} />取消</button>}
        </form>
        {error && <div className="error">{error}</div>}
      </section>
      <AdminTableToolbar
        query={query}
        onQuery={setQuery}
        departments={config.fields.some(([name]) => name === 'departmentId') ? departments : []}
        departmentFilter={departmentFilter}
        onDepartmentFilter={setDepartmentFilter}
        grades={config.title === '学生管理' ? grades : []}
        gradeFilter={gradeFilter}
        onGradeFilter={setGradeFilter}
      />
      <AdminDataTable
        rows={filteredItems.map((row, index) => ({ ...row, display_index: index + 1 }))}
        columns={config.columns}
        labels={config.labels}
        loading={loading}
        resourceTitle={config.title.replace(/管理$/, '')}
        onEdit={config.noEdit ? null : edit}
        onDelete={remove}
      />
    </div>
  );
}

function AdminPageHead({ title, subtitle, icon, count, onRefresh }) {
  return (
    <section className="pageHead adminHead">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      <div className="summaryStrip">
        {count !== undefined && <span>{icon}{count} 条</span>}
        <button className="ghost" onClick={onRefresh}><RefreshCw size={16} />刷新</button>
      </div>
    </section>
  );
}

function StudentTableHeader({ filters, onFilters, departments, grades, total, onApply, onReset, onCreate }) {
  return (
    <div className="studentTableHeader">
      <div className="studentSearchBar">
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="请输入姓名或学号"
              value={filters.query}
              onChange={(event) => onFilters({ ...filters, query: event.target.value })}
            />
          </span>
        </label>
        <label>
          院系
          <select value={filters.department} onChange={(event) => onFilters({ ...filters, department: event.target.value })}>
            <option>全部院系</option>
            {departments.map((department) => <option key={department.id}>{department.name}</option>)}
          </select>
        </label>
        <label>
          年级
          <select value={filters.grade} onChange={(event) => onFilters({ ...filters, grade: event.target.value })}>
            {grades.map((grade) => <option key={grade}>{grade}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="button" onClick={onApply}><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </div>
      <div className="studentTableActions">
        <strong>共搜索到 {total} 位学生</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />新增学生</button>
      </div>
    </div>
  );
}

function TeacherTableHeader({ filters, onFilters, departments, total, onApply, onReset, onCreate }) {
  function submitFilters(event) {
    event.preventDefault();
    onApply();
  }

  return (
    <div className="studentTableHeader">
      <form className="studentSearchBar teacherSearchBar" onSubmit={submitFilters}>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="请输入教师姓名或账号"
              value={filters.query}
              onChange={(event) => onFilters({ ...filters, query: event.target.value })}
            />
          </span>
        </label>
        <label>
          院系
          <select value={filters.department} onChange={(event) => onFilters({ ...filters, department: event.target.value })}>
            <option>全部院系</option>
            {departments.map((department) => <option key={department.id}>{department.name}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="submit"><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </form>
      <div className="studentTableActions">
        <strong>共 {total} 位教师</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />新增教师</button>
      </div>
    </div>
  );
}

function ClassroomTableHeader({ filters, onFilters, buildings, total, onApply, onReset, onCreate }) {
  function submitFilters(event) {
    event.preventDefault();
    onApply();
  }

  return (
    <div className="studentTableHeader">
      <form className="studentSearchBar teacherSearchBar" onSubmit={submitFilters}>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="请输入教室名称，如：101"
              value={filters.query}
              onChange={(event) => onFilters({ ...filters, query: event.target.value })}
            />
          </span>
        </label>
        <label>
          教学楼
          <select value={filters.building} onChange={(event) => onFilters({ ...filters, building: event.target.value })}>
            <option>全部教学楼</option>
            {buildings.map((building) => <option key={building}>{building}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="submit"><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </form>
      <div className="studentTableActions">
        <strong>共 {total} 间教室</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />新增教室</button>
      </div>
    </div>
  );
}

function ClassroomFormDialog({ title, form, buildings, isEdit, onForm, onSubmit, onClose }) {
  return (
    <div className="modalLayer">
      <section className="modal classroomDialog" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modalHead">
          <div>
            <h2>{title}</h2>
            <p>填写教室基础信息后保存。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label={`关闭${title}`}><X size={18} /></button>
        </div>
        <form className="modalBody classroomForm" onSubmit={onSubmit}>
          <label>
            <span className="requiredLabel"><i>*</i>教室名称</span>
            <input
              type="text"
              placeholder="请输入，如：101"
              value={form.name ?? ''}
              onChange={(event) => onForm({ ...form, name: event.target.value })}
              required
            />
          </label>
          <label>
            <span className="requiredLabel"><i>*</i>教学楼</span>
            <select
              value={form.building ?? ''}
              onChange={(event) => onForm({ ...form, building: event.target.value })}
              required
            >
              <option value="">请选择所属教学楼</option>
              {buildings.map((building) => <option key={building} value={building}>{building}</option>)}
            </select>
          </label>
          <label>
            <span className="requiredLabel"><i>*</i>容量</span>
            <input
              type="number"
              min="1"
              step="1"
              placeholder="请输入容纳人数"
              value={form.capacity ?? ''}
              onChange={(event) => onForm({ ...form, capacity: event.target.value })}
              required
            />
          </label>
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit"><Save size={16} />{isEdit ? '保存' : '确定'}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

const gradeYearOptions = (() => {
  const current = new Date().getFullYear();
  return Array.from({ length: 8 }, (_, i) => current - i);
})();

const fieldPlaceholders = {
  name: '请输入姓名',
  username: '请输入登录账号',
  password: '留空则使用默认密码 (123456)',
  studentNo: '请输入学号',
  departmentId: '请选择所属院系',
};

const requiredFields = new Set(['name', 'username', 'studentNo', 'departmentId', 'grade']);

function AdminResourceFormDialog({ title, fields, form, departments, submitLabel, onForm, onSubmit, onClose }) {
  const resourceName = title.includes('教师') ? '教师' : title.includes('教室') ? '教室' : '学生';
  // Determine if the last non-action field is odd-positioned (needs full-width)
  const lastFieldIdx = fields.length - 1;
  const lastFieldIsOdd = fields.length % 2 === 1;
  return (
    <div className="modalLayer">
      <section className="modal adminResourceDialog" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modalHead">
          <div>
            <h2>{title}</h2>
            <p>填写{resourceName}基础信息后保存。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label={`关闭${title}`}><X size={18} /></button>
        </div>
        <form className="modalBody adminResourceForm" onSubmit={onSubmit}>
          {fields.map(([name, label, meta = {}], idx) => {
            const isRequired = meta.required ?? requiredFields.has(name);
            const placeholder = meta.placeholder ?? fieldPlaceholders[name] ?? '';
            const isFullWidth = lastFieldIsOdd && idx === lastFieldIdx;
            return (
              <label key={name} className={isFullWidth ? 'fullWidthField' : ''}>
                <span className="requiredLabel">
                  {isRequired && <i>*</i>}{label}
                </span>
                {name === 'departmentId' ? (
                  <select
                    value={form[name] ?? ''}
                    required={isRequired}
                    onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                  >
                    <option value="">请选择所属院系</option>
                    {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                  </select>
                ) : (meta.type === 'year' || name === 'grade') ? (
                  <select
                    value={form[name] ?? ''}
                    required={isRequired}
                    onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                  >
                    <option value="">请选择入学年份</option>
                    {gradeYearOptions.map((year) => (
                      <option key={year} value={String(year)}>{year} 级</option>
                    ))}
                  </select>
                ) : (
                  <input
                    type={name === 'password' ? 'text' : 'text'}
                    placeholder={placeholder}
                    required={isRequired}
                    value={form[name] ?? ''}
                    onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                  />
                )}
              </label>
            );
          })}
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit">确定</button>
          </div>
        </form>
      </section>
    </div>
  );
}

function AdminDepartmentsPage({ client }) {
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState({ name: '' });
  const [draftFilters, setDraftFilters] = useState({ query: '' });
  const [appliedFilters, setAppliedFilters] = useState({ query: '' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const filteredDepartments = useMemo(() => {
    const query = appliedFilters.query.trim().toLowerCase();
    if (!query) return departments;
    return departments.filter((department) =>
      String(department.name ?? '').toLowerCase().includes(query)
    );
  }, [departments, appliedFilters]);

  const totalPages = Math.max(1, Math.ceil(filteredDepartments.length / pageSize));
  const pageDepartments = filteredDepartments.slice((page - 1) * pageSize, page * pageSize);
  const displayedRows = pageDepartments.map((row, index) => ({
    ...row,
    display_index: (page - 1) * pageSize + index + 1,
  }));

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setDepartments(await client.get('/admin/departments'));
    } catch (err) {
      setError(err.message ?? '院系数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    setEditId(null);
    setForm({ name: '' });
    setDialogOpen(true);
  }

  function openEdit(row) {
    setEditId(row.id);
    setForm({ name: row.name ?? '' });
    setDialogOpen(true);
  }

  function closeDialog() {
    setDialogOpen(false);
    setEditId(null);
    setForm({ name: '' });
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    try {
      if (editId) {
        await client.put(`/admin/departments/${editId}`, { name: form.name });
        setMessage('院系已更新');
      } else {
        await client.post('/admin/departments', { name: form.name });
        setMessage('院系已创建');
      }
      closeDialog();
      await load();
    } catch (err) {
      setError(err.message ?? '操作失败');
    }
  }

  async function remove(id) {
    if (!window.confirm('确认删除该院系？删除后不可恢复。')) return;
    setError('');
    setMessage('');
    try {
      await client.delete(`/admin/departments/${id}`);
      setMessage('院系已删除');
      await load();
    } catch (err) {
      setError(err.message ?? '删除失败');
    }
  }

  function applyFilters(event) {
    if (event) event.preventDefault();
    setAppliedFilters(draftFilters);
    setPage(1);
  }

  function resetFilters() {
    const empty = { query: '' };
    setDraftFilters(empty);
    setAppliedFilters(empty);
    setPage(1);
  }

  return (
    <div className="adminPage">
      <AdminPageHead title="院系管理" subtitle="维护院系基础数据，可新增、编辑或删除院系。" onRefresh={load} />
      {error && <div className="error">{error}</div>}
      {message && <div className="success">{message}</div>}
      <div className="panel adminTablePanel">
        <div className="studentTableHeader">
          <form className="studentSearchBar teacherSearchBar" onSubmit={applyFilters}>
            <label className="searchField">
              搜索
              <span>
                <Search size={16} />
                <input
                  placeholder="请输入院系名称"
                  value={draftFilters.query}
                  onChange={(event) => setDraftFilters({ ...draftFilters, query: event.target.value })}
                />
              </span>
            </label>
            <div className="studentSearchActions">
              <button type="submit"><Search size={16} />查询</button>
              <button type="button" className="ghost" onClick={resetFilters}>重置</button>
            </div>
          </form>
          <div className="studentTableActions">
            <strong>共 {filteredDepartments.length} 个院系</strong>
            <button type="button" onClick={openCreate}><Plus size={16} />新增院系</button>
          </div>
        </div>
        <div className="tableWrap">
          <table className="adminTable">
            <thead>
              <tr>
                <th>序号</th>
                <th>院系名称</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan="3">加载中</td></tr>}
              {!loading && displayedRows.map((row) => (
                <tr key={row.id}>
                  <td>{row.display_index}</td>
                  <td><strong>{row.name}</strong></td>
                  <td>
                    <div className="actions">
                      <button type="button" className="ghost" aria-label={`编辑 ${row.name}`} onClick={() => openEdit(row)}>
                        <PenLine size={15} />编辑
                      </button>
                      <button type="button" className="ghost" aria-label={`删除 ${row.name}`} onClick={() => remove(row.id)}>
                        <X size={15} />删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && !displayedRows.length && <tr><td colSpan="3">暂无院系数据</td></tr>}
            </tbody>
          </table>
        </div>
        <AdminPagination
          total={filteredDepartments.length}
          page={page}
          totalPages={totalPages}
          pageSize={pageSize}
          onPage={setPage}
          onPageSize={(nextSize) => {
            setPageSize(nextSize);
            setPage(1);
          }}
        />
      </div>
      {dialogOpen && (
        <div className="modalLayer">
          <section className="modal adminResourceDialog" role="dialog" aria-modal="true" aria-label={editId ? '编辑院系' : '新增院系'}>
            <div className="modalHead">
              <div>
                <h2>{editId ? '编辑院系' : '新增院系'}</h2>
                <p>{editId ? '修改院系名称后保存。' : '填写院系名称后创建。'}</p>
              </div>
              <button type="button" className="iconButton" onClick={closeDialog} aria-label="关闭"><X size={18} /></button>
            </div>
            <form className="modalBody adminResourceForm" onSubmit={submit}>
              <label className="fullWidthField">
                <span className="requiredLabel"><i>*</i>院系名称</span>
                <input
                  type="text"
                  placeholder="请输入院系名称，如：计算机科学与技术学院"
                  required
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                  autoFocus
                />
              </label>
              <div className="formActions dialogActions">
                <button type="button" className="ghost" onClick={closeDialog}>取消</button>
                <button type="submit"><Save size={16} />{editId ? '保存' : '确定'}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

function AdminPagination({ total, page, totalPages, pageSize, onPage, onPageSize }) {
  return (
    <div className="adminPagination">
      <span>共 {total} 条</span>
      <label>
        每页条数
        <select value={pageSize} onChange={(event) => onPageSize(Number(event.target.value))}>
          {[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <button type="button" className="ghost" disabled={page <= 1} onClick={() => onPage(Math.max(1, page - 1))}>上一页</button>
      <strong>第 {page} / {totalPages} 页</strong>
      <button type="button" className="ghost" disabled={page >= totalPages} onClick={() => onPage(Math.min(totalPages, page + 1))}>下一页</button>
    </div>
  );
}

function AdminTableToolbar({ query, onQuery, departments = [], departmentFilter, onDepartmentFilter, grades = [], gradeFilter, onGradeFilter }) {
  return (
    <section className="adminTableToolbar panel">
      <label className="searchField">
        搜索
        <span>
          <Search size={16} />
          <input placeholder="搜索当前表格" value={query} onChange={(event) => onQuery(event.target.value)} />
        </span>
      </label>
      {departments.length > 0 && (
        <label>
          院系
          <select value={departmentFilter} onChange={(event) => onDepartmentFilter(event.target.value)}>
            <option>全部院系</option>
            {departments.map((department) => <option key={department.id}>{department.name}</option>)}
          </select>
        </label>
      )}
      {grades.length > 0 && (
        <label>
          年级
          <select value={gradeFilter} onChange={(event) => onGradeFilter(event.target.value)}>
            {grades.map((grade) => <option key={grade}>{grade}</option>)}
          </select>
        </label>
      )}
    </section>
  );
}

function AdminDataTable({ rows, columns, labels = {}, loading, resourceTitle = '', onEdit, onDelete, onResetPassword, header = null, footer = null }) {
  const hasActions = Boolean(onEdit || onDelete || onResetPassword);
  return (
    <div className="panel adminTablePanel">
      {header}
      <div className="tableWrap">
        <table className="adminTable">
          <thead>
            <tr>
              {columns.map((column) => <th key={column}>{labels[column] ?? adminLabels[column] ?? column}</th>)}
              {hasActions && <th>操作</th>}
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={columns.length + (hasActions ? 1 : 0)}>加载中</td></tr>}
            {!loading && rows.map((row) => (
              <tr key={row.id ?? `${row.session_id}-${displayAdminRow(row)}`}>
                {columns.map((column) => <td key={column}>{formatAdminCell(row, column)}</td>)}
                {hasActions && (
                  <td>
                    <div className="actions">
                      {onEdit && <button type="button" className="ghost" aria-label={`编辑 ${resourceTitle} ${displayAdminRow(row)}`} onClick={() => onEdit(row)}><PenLine size={15} />编辑</button>}
                      {onResetPassword && <button type="button" className="ghost" aria-label={`重置密码 ${resourceTitle} ${displayAdminRow(row)}`} onClick={() => onResetPassword(row)}><KeyRound size={15} />重置密码</button>}
                      {onDelete && <button type="button" className="ghost" aria-label={`删除 ${resourceTitle} ${displayAdminRow(row)}`} onClick={() => onDelete(row.id)}><X size={15} />删除</button>}
                    </div>
                  </td>
                )}
              </tr>
            ))}
            {!loading && !rows.length && <tr><td colSpan={columns.length + (hasActions ? 1 : 0)}>暂无数据</td></tr>}
          </tbody>
        </table>
      </div>
      {footer}
    </div>
  );
}

function emptyAdminForm(fields) {
  return Object.fromEntries(fields.map(([name]) => [name, '']));
}

function normalizeAdminForm(form) {
  return Object.fromEntries(Object.entries(form).map(([key, value]) => {
    const numeric = ['classId', 'courseId', 'teacherId', 'assignmentId', 'studentId', 'departmentId'].includes(key);
    return [key, numeric && value !== '' ? Number(value) : value];
  }));
}

function adminFieldValue(row, name) {
  if (name === 'password') return '';
  const map = {
    studentNo: 'student_no',
    classId: 'class_id',
    courseId: 'course_id',
    teacherId: 'teacher_id',
    assignmentId: 'assignment_id',
    studentId: 'student_id',
    departmentId: 'department_id',
  };
  return String(row[name] ?? row[map[name]] ?? '');
}

function filterRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => Object.values(row).some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

function filterStudentRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => [row.name, row.student_no].some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

function filterTeacherRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => [row.name, row.username].some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

function filterClassroomRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => [row.name, row.building].some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

function filterAdminCourses(courses, filters) {
  const query = filters.query.trim().toLowerCase();
  return courses.filter((course) => {
    const matchesQuery = !query || [course.name, course.code].some((cell) => String(cell ?? '').toLowerCase().includes(query));
    const matchesDepartment = filters.department === '全部院系' || course.department_name === filters.department;
    const matchesTerm = filters.term === '全部学期' || course.term === filters.term;
    return matchesQuery && matchesDepartment && matchesTerm;
  });
}

function displayAdminRow(row) {
  return courseNameText(row.name || row.course_name || row.student_name || row.username || `#${row.id}`);
}

function formatAdminCell(row, column) {
  if (column === 'status') return statusText(row[column]);
  if (column === 'source') return sourceText(row[column]);
  if (column === 'course_name' || column === 'name') return courseNameText(row[column]);
  return String(row[column] ?? '');
}

function sessionTotals(sessions) {
  return sessions.reduce(
    (sum, row) => ({
      present: sum.present + Number(row.present_count ?? 0),
      absent: sum.absent + Number(row.absent_count ?? 0),
    }),
    { present: 0, absent: 0 },
  );
}

function formatDate(value) {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

function formatCourseSchedule(course) {
  if (!course.weekday && !course.start_time && !course.end_time) return '未排课';
  const time = [course.start_time, course.end_time].filter(Boolean).join('-');
  return [course.weekday, time].filter(Boolean).join(' ');
}

function formatToday() {
  return new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  });
}

function formatSeconds(value) {
  const minutes = Math.floor(value / 60);
  const seconds = value % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
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
