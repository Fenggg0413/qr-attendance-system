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
  FileText,
  Fullscreen,
  KeyRound,
  LogOut,
  Menu,
  PenLine,
  PieChart,
  Plus,
  RefreshCw,
  Save,
  Search,
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
      ['name', '姓名'],
      ['username', '账号'],
      ['password', '初始密码'],
      ['studentNo', '学号'],
      ['grade', '年级'],
      ['departmentId', '所属院系'],
    ],
    columns: ['id', 'name', 'username', 'student_no', 'department_name', 'grade'],
  },
  teachers: {
    title: '教师管理',
    endpoint: '/admin/teachers',
    icon: UsersRound,
    fields: [
      ['name', '姓名'],
      ['username', '账号'],
      ['password', '初始密码'],
      ['departmentId', '所属院系'],
    ],
    columns: ['name', 'username', 'department_name'],
  },
};

const adminNav = [
  ['dashboard', '仪表盘', BarChart3],
  ['students', '学生管理', UserRound],
  ['teachers', '教师管理', UsersRound],
  ['courses', '课程管理', BookOpen],
];

const adminLabels = {
  id: 'ID',
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
  const [teacherView, setTeacherView] = useState('courses');
  const [adminView, setAdminView] = useState('dashboard');
  const [breadcrumb, setBreadcrumb] = useState(['首页', '我的课程']);
  const [collapsed, setCollapsed] = useState(false);

  function onLogin(nextSession) {
    localStorage.setItem('qr-attendance-session', JSON.stringify(nextSession));
    setSession(nextSession);
    setTeacherView('courses');
    setAdminView('dashboard');
  }

  function logout() {
    localStorage.removeItem('qr-attendance-session');
    setSession(null);
    setTeacherView('courses');
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
              <button className={teacherView !== 'profile' ? 'active' : ''} onClick={() => setTeacherView('courses')}>
                <BookOpen size={17} />
                <span>我的课程</span>
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
        <p className="hint">默认账号：管理员 admin/admin123，教师 teacher1/teacher123</p>
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
  const [modalCourse, setModalCourse] = useState(null);
  const [drawerSession, setDrawerSession] = useState(null);
  const [drawerRecords, setDrawerRecords] = useState([]);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    setBreadcrumb(['首页', view === 'profile' ? '个人中心' : selectedCourse ? '课程详情' : '我的课程']);
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

  async function openRecordsDrawer(sessionRow) {
    setDrawerSession(sessionRow);
    setDrawerRecords(await client.get(`/teacher/attendance-sessions/${sessionRow.id}/records`));
  }

  if (view === 'profile') {
    return <TeacherProfile client={client} logout={logout} />;
  }

  return (
    <>
      {!selectedCourse ? (
        <CoursesWorkspace
          courses={courses}
          error={coursesError}
          onOpenDetail={openDetail}
          onStart={(course) => setModalCourse(course)}
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
          onStart={() => setModalCourse(detail ?? selectedCourse)}
          onOpenRecords={openRecordsDrawer}
          onStudentsChange={setStudents}
        />
      )}
      {modalCourse && (
        <AttendanceModal
          client={client}
          course={modalCourse}
          onClose={() => setModalCourse(null)}
          onChanged={refreshCourseData}
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

function CoursesWorkspace({ courses, error, onOpenDetail, onStart }) {
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
              <button className="ghost" onClick={() => onOpenDetail(course)}>查看详情</button>
              <button onClick={() => onStart(course)}><Plus size={16} />发起考勤</button>
            </div>
          </article>
        ))}
        {!filtered.length && <div className="empty panel">没有匹配的课程</div>}
      </section>
    </div>
  );
}

function CourseDetail({ client, course, sessions, students, activeTab, loading, onBack, onTab, onStart, onOpenRecords, onStudentsChange }) {
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
        <button onClick={onStart}><Plus size={16} />发起考勤</button>
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
          {loading ? <p className="hint">加载中</p> : <SessionsTable sessions={sessions} onOpenRecords={onOpenRecords} />}
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

function SessionsTable({ sessions, onOpenRecords }) {
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
              <td><button className="ghost" onClick={() => onOpenRecords(row)}>查看明细</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StudentsPanel({ client, students, onStudentsChange }) {
  const [savingId, setSavingId] = useState(null);
  const sorted = [...students].sort((a, b) => `${a.student_no}${a.name}`.localeCompare(`${b.student_no}${b.name}`, 'zh-Hans-CN'));

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
        <span className="muted">{students.length} 人</span>
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
            {sorted.map((student) => (
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

function AttendanceModal({ client, course, onClose, onChanged }) {
  const [step, setStep] = useState(1);
  const [durationMinutes, setDurationMinutes] = useState(5);
  const [activeSession, setActiveSession] = useState(null);
  const [qr, setQr] = useState(null);
  const [records, setRecords] = useState([]);
  const [error, setError] = useState('');
  const [ended, setEnded] = useState(false);
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (!activeSession || ended) return undefined;
    let cancelled = false;
    async function refresh() {
      try {
        const [nextQr, nextRecords] = await Promise.all([
          client.get(`/teacher/attendance-sessions/${activeSession.id}/qr`),
          client.get(`/teacher/attendance-sessions/${activeSession.id}/records`),
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
  }, [activeSession, client, ended]);

  useEffect(() => {
    if (!activeSession || ended) return undefined;
    function tick() {
      const next = Math.max(0, Math.floor((new Date(activeSession.endsAt ?? activeSession.ends_at).getTime() - Date.now()) / 1000));
      setRemaining(next);
      if (next === 0) setEnded(true);
    }
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [activeSession, ended]);

  async function start() {
    setError('');
    try {
      const created = await client.post(`/teacher/courses/${course.id}/attendance-sessions`, { method: 'QR', durationMinutes });
      setActiveSession(created);
      setStep(2);
      await onChanged();
    } catch (err) {
      setError(err.message ?? '创建考勤失败');
    }
  }

  async function closeSession() {
    if (!activeSession) return;
    await client.post(`/teacher/attendance-sessions/${activeSession.id}/close`, {});
    setEnded(true);
    await onChanged();
  }

  return (
    <div className="modalLayer">
      <section className="modal" role="dialog" aria-modal="true" aria-label="发起考勤">
        <div className="modalHead">
          <div>
            <h2>发起考勤</h2>
            <p>{courseNameText(course.name)} · {course.class_name}</p>
          </div>
          <button className="iconButton" onClick={onClose} aria-label="关闭弹窗"><X size={18} /></button>
        </div>

        {step === 1 ? (
          <div className="modalBody">
            <div className="fixedMethod">
              <ClipboardCheck size={18} />
              <div>
                <strong>二维码考勤</strong>
                <span>发起后展示动态二维码，学生扫码完成签到。</span>
              </div>
            </div>
            <label>
              开放时长
              <input type="number" min="1" max="120" value={durationMinutes} onChange={(event) => setDurationMinutes(Number(event.target.value))} />
            </label>
            {error && <div className="error">{error}</div>}
            <div className="actions right">
              <button className="ghost" onClick={onClose}>取消</button>
              <button onClick={start}>立即开始</button>
            </div>
          </div>
        ) : (
          <div className="modalBody attendanceLive">
            <div className="liveHeader">
              <h3>动态签到码</h3>
              <span className={ended ? 'pill' : 'pill live'}>{ended ? '考勤已结束' : `剩余 ${formatSeconds(remaining)}`}</span>
            </div>
            <div className="qrPanel">
              {qr?.payload && <QRCodeSVG value={qr.payload} size={190} />}
              {qr?.expiresAt && <p className="hint">刷新时间：{new Date(qr.expiresAt).toLocaleTimeString()}</p>}
              {error && <div className="error">{error}</div>}
            </div>
            <RecordsTable records={records} />
            <div className="actions right">
              <button className="ghost" onClick={onClose}>关闭</button>
              <button disabled={ended} onClick={closeSession}>提前结束考勤</button>
            </div>
          </div>
        )}
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
        <RecordsTable records={records} />
      </aside>
    </div>
  );
}

function RecordsTable({ records }) {
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
              <td><span className="pill">{statusText(record.status)}</span></td>
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

function AdminPortal({ session, view, setBreadcrumb, logout }) {
  const client = useMemo(() => api.withToken(session.token), [session.token]);
  const config = adminResources[view];
  const title = view === 'dashboard' ? '仪表盘' : view === 'courses' ? '课程管理' : config?.title ?? '后台管理';

  useEffect(() => {
    setBreadcrumb(['首页', title]);
  }, [setBreadcrumb, title]);

  if (view === 'dashboard') return <AdminDashboard client={client} session={session} onAuthExpired={logout} />;
  if (view === 'courses') return <AdminCoursesPage client={client} />;
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

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
    await client.post('/admin/courses', normalizeAdminForm(form));
    setForm({ name: '', code: '', departmentId: departments[0] ? String(departments[0].id) : '' });
    await load();
  }

  async function openCourse(course) {
    setSelected(course);
    setDetail(await client.get(`/admin/courses/${course.id}`));
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
      <AdminPageHead title="课程管理" subtitle="创建课程并进入详情维护排课、教师和学生名单。" icon={<BookOpen />} count={courses.length} onRefresh={load} />
      <section className="panel adminEditor">
        <form className="inlineForm" onSubmit={createCourse}>
          <label>课程名称<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
          <label>课程代码<input value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} /></label>
          <label>
            院系
            <select value={form.departmentId} onChange={(event) => setForm({ ...form, departmentId: event.target.value })}>
              {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
            </select>
          </label>
          <button><Plus size={16} />添加课程</button>
        </form>
        {error && <div className="error">{error}</div>}
      </section>
      {loading ? <div className="panel">加载中</div> : (
        <section className="adminCourseGrid">
          {courses.map((course) => (
            <article className="adminCourseCard" key={course.id}>
              <div>
                <span className="eyebrow">{course.department_name ?? '未设置院系'}</span>
                <h2>{course.name}</h2>
                <p>{course.code}</p>
                <small><CalendarDays size={14} />{course.weekday ?? '-'} {course.start_time ?? ''}-{course.end_time ?? ''} · {course.location ?? '未排课'}</small>
              </div>
              <button className="ghost" aria-label={`查看课程 ${course.name}`} onClick={() => openCourse(course)}>查看详情</button>
            </article>
          ))}
          {!courses.length && <div className="empty panel">暂无课程</div>}
        </section>
      )}
    </div>
  );
}

function AdminCourseDetail({ client, detail, onBack, onChanged }) {
  const course = detail.course ?? {};
  const hasSavedTeacher = Boolean(detail.teacher?.teacher_id ?? detail.teacher?.id);
  const [schedule, setSchedule] = useState({
    weekday: detail.schedule?.weekday ?? '',
    startTime: detail.schedule?.start_time ?? '',
    endTime: detail.schedule?.end_time ?? '',
    location: detail.schedule?.location ?? '',
  });
  const [teacher, setTeacher] = useState({
    teacherId: detail.teacher?.teacher_id ?? detail.teacher?.id ?? '',
    term: detail.teacher?.term ?? '',
  });
  const [teachers, setTeachers] = useState([]);
  const [students, setStudents] = useState([]);
  const [terms, setTerms] = useState([]);
  const [teacherSearch, setTeacherSearch] = useState('');
  const [studentSearch, setStudentSearch] = useState('');
  const [selectedStudentIds, setSelectedStudentIds] = useState([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [studentDialogOpen, setStudentDialogOpen] = useState(false);

  useEffect(() => {
    setSchedule({
      weekday: detail.schedule?.weekday ?? '',
      startTime: detail.schedule?.start_time ?? '',
      endTime: detail.schedule?.end_time ?? '',
      location: detail.schedule?.location ?? '',
    });
    setTeacher({
      teacherId: detail.teacher?.teacher_id ?? detail.teacher?.id ?? '',
      term: detail.teacher?.term ?? '',
    });
    setSelectedStudentIds([]);
    setStudentSearch('');
    setStudentDialogOpen(false);
  }, [detail]);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      client.get('/admin/teachers'),
      client.get('/admin/students'),
      client.get('/admin/terms'),
    ])
      .then(([teachersResult, studentsResult, termsResult]) => {
        if (cancelled) return;
        if (teachersResult.status === 'fulfilled') setTeachers(teachersResult.value);
        if (studentsResult.status === 'fulfilled') setStudents(studentsResult.value);
        setTerms(termsResult.status === 'fulfilled' ? termsResult.value : fallbackTerms);
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

  async function saveSchedule(event) {
    event.preventDefault();
    setError('');
    try {
      await client.put(`/admin/courses/${course.id}/schedule`, schedule);
      setMessage('排课已保存');
      await onChanged();
    } catch (err) {
      setError(err.message ?? '排课保存失败');
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

  return (
    <div className="adminPage">
      <section className="pageHead detailHead">
        <button className="iconButton" onClick={onBack} aria-label="返回课程列表"><ChevronLeft size={18} /></button>
        <div>
          <h1>{course.name}</h1>
          <p>{course.code} · {course.department_name ?? '未设置院系'}</p>
        </div>
      </section>
      {message && <div className="success">{message}</div>}
      {error && <div className="error">{error}</div>}
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
        <form className="courseDetailSection" onSubmit={saveSchedule}>
          <div>
            <h3>排课管理</h3>
          </div>
          <div className="courseDetailFields">
            <label>
              星期
              <select value={schedule.weekday} onChange={(event) => setSchedule({ ...schedule, weekday: event.target.value })}>
                <option value="">请选择星期</option>
                {weekDays.map((weekday) => <option key={weekday} value={weekday}>{weekday}</option>)}
              </select>
            </label>
            <label>开始时间<input type="time" value={schedule.startTime} onChange={(event) => setSchedule({ ...schedule, startTime: event.target.value })} /></label>
            <label>结束时间<input type="time" value={schedule.endTime} onChange={(event) => setSchedule({ ...schedule, endTime: event.target.value })} /></label>
            <label>上课地点<input value={schedule.location} onChange={(event) => setSchedule({ ...schedule, location: event.target.value })} /></label>
          </div>
          <div className="formActions"><button><Save size={16} />保存排课</button></div>
        </form>
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
          onSearch={setStudentSearch}
          onToggleStudent={toggleStudent}
          onClose={() => {
            setStudentDialogOpen(false);
            setSelectedStudentIds([]);
            setStudentSearch('');
          }}
          onConfirm={addSelectedStudents}
        />
      )}
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

function StudentPickerDialog({ students, search, selectedStudentIds, onSearch, onToggleStudent, onClose, onConfirm }) {
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
              <thead><tr><th>选择</th><th>姓名</th><th>学号</th><th>院系</th></tr></thead>
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
  const [studentPage, setStudentPage] = useState(1);
  const [studentPageSize, setStudentPageSize] = useState(10);
  const [teacherPage, setTeacherPage] = useState(1);
  const [teacherPageSize, setTeacherPageSize] = useState(10);
  const [editId, setEditId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const grades = ['全部年级', ...Array.from(new Set(items.map((item) => item.grade).filter(Boolean)))];
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
    return filterRows(items, query).filter((item) => {
      const inDepartment = departmentFilter === '全部院系' || item.department_name === departmentFilter;
      return inDepartment;
    });
  }, [departmentFilter, isStudentResource, isTeacherResource, items, query, studentAppliedFilters, teacherAppliedFilters]);
  const studentTotalPages = Math.max(1, Math.ceil(filteredItems.length / studentPageSize));
  const teacherTotalPages = Math.max(1, Math.ceil(filteredItems.length / teacherPageSize));
  const studentRows = isStudentResource
    ? filteredItems.slice((studentPage - 1) * studentPageSize, studentPage * studentPageSize)
    : filteredItems;
  const teacherRows = isTeacherResource
    ? filteredItems.slice((teacherPage - 1) * teacherPageSize, teacherPage * teacherPageSize)
    : filteredItems;

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
    setStudentPage(1);
    setTeacherPage(1);
    setMessage('');
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, config]);

  useEffect(() => {
    setStudentPage((current) => Math.min(current, studentTotalPages));
  }, [studentTotalPages]);

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

  async function resetTeacherPassword(row) {
    setError('');
    setMessage('');
    if (!window.confirm(`确认将「${row.name}」的密码重置为 teacher123？`)) return;
    try {
      await client.post(`${config.endpoint}/${row.id}/reset-password`, {});
      setMessage(`${row.name}的密码已重置为 teacher123`);
    } catch (err) {
      setError(err.message);
    }
  }

  if (isStudentResource) {
    return (
      <div className="adminPage">
        <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" onRefresh={load} />
        {error && <div className="error">{error}</div>}
        <AdminDataTable
          rows={studentRows}
          columns={config.columns}
          loading={loading}
          resourceTitle={config.title.replace(/管理$/, '')}
          onEdit={config.noEdit ? null : edit}
          onDelete={remove}
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
            title={editId && !config.noEdit ? '编辑学生' : '新增学生'}
            fields={config.fields}
            form={form}
            departments={departments}
            submitLabel={editId && !config.noEdit ? '保存' : '新增'}
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
          rows={teacherRows}
          columns={config.columns}
          loading={loading}
          resourceTitle={config.title.replace(/管理$/, '')}
          onEdit={config.noEdit ? null : edit}
          onDelete={remove}
          onResetPassword={resetTeacherPassword}
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
            fields={editId && !config.noEdit ? config.fields.filter(([name]) => name !== 'password') : config.fields}
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

  return (
    <div className="adminPage">
      <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" icon={<config.icon />} count={items.length} onRefresh={load} />
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
        departments={departments}
        departmentFilter={departmentFilter}
        onDepartmentFilter={setDepartmentFilter}
        grades={config.title === '学生管理' ? grades : []}
        gradeFilter={gradeFilter}
        onGradeFilter={setGradeFilter}
      />
      <AdminDataTable
        rows={filteredItems}
        columns={config.columns}
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

function AdminResourceFormDialog({ title, fields, form, departments, submitLabel, onForm, onSubmit, onClose }) {
  const resourceName = title.includes('教师') ? '教师' : '学生';
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
          {fields.map(([name, label]) => (
            <label key={name}>
              {label}
              {name === 'departmentId' ? (
                <select value={form[name] ?? ''} onChange={(event) => onForm({ ...form, [name]: event.target.value })}>
                  <option value="">请选择院系</option>
                  {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                </select>
              ) : (
                <input
                  type="text"
                  value={form[name] ?? ''}
                  onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                />
              )}
            </label>
          ))}
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit">{submitLabel === '保存' ? <Save size={16} /> : <Plus size={16} />}{submitLabel}</button>
          </div>
        </form>
      </section>
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

function AdminDataTable({ rows, columns, loading, resourceTitle = '', onEdit, onDelete, onResetPassword, header = null, footer = null }) {
  const hasActions = Boolean(onEdit || onDelete || onResetPassword);
  return (
    <div className="panel adminTablePanel">
      {header}
      <div className="tableWrap">
        <table className="adminTable">
          <thead>
            <tr>
              {columns.map((column) => <th key={column}>{adminLabels[column] ?? column}</th>)}
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
