import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  BarChart3,
  Bell,
  BookOpen,
  Building2,
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
  Plus,
  RefreshCw,
  Save,
  Search,
  ShieldCheck,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import './styles.css';
import { api, ApiError } from './services/api';

const adminResources = {
  teachers: {
    title: '教师管理',
    endpoint: '/admin/teachers',
    icon: UsersRound,
    fields: [
      ['name', '姓名'],
      ['username', '账号'],
      ['password', '初始密码'],
      ['department', '院系'],
    ],
    columns: ['id', 'name', 'username', 'department'],
  },
  students: {
    title: '学生管理',
    endpoint: '/admin/students',
    icon: UserRound,
    fields: [
      ['name', '姓名'],
      ['username', '账号'],
      ['password', '初始密码'],
      ['studentNo', '学号'],
      ['classId', '班级编号'],
    ],
    columns: ['id', 'name', 'username', 'student_no', 'class_name'],
  },
  classes: {
    title: '班级管理',
    endpoint: '/admin/classes',
    icon: Building2,
    fields: [
      ['name', '班级名'],
      ['grade', '年级'],
    ],
    columns: ['id', 'name', 'grade'],
  },
  courses: {
    title: '课程管理',
    endpoint: '/admin/courses',
    icon: BookOpen,
    fields: [
      ['name', '课程名'],
      ['code', '课程代码'],
      ['classId', '班级编号'],
    ],
    columns: ['id', 'name', 'code', 'class_name'],
  },
  assignments: {
    title: '课程分配',
    endpoint: '/admin/course-assignments',
    icon: ClipboardCheck,
    fields: [
      ['courseId', '课程编号'],
      ['teacherId', '教师编号'],
      ['term', '学期'],
    ],
    columns: ['id', 'course_name', 'teacher_name', 'term'],
  },
  enrollments: {
    title: '选课名单',
    endpoint: '/admin/enrollments',
    icon: ClipboardCheck,
    fields: [
      ['assignmentId', '分配 ID'],
      ['studentId', '学生 ID'],
    ],
    columns: ['id', 'course_name', 'teacher_name', 'student_name', 'student_no'],
    noEdit: true,
  },
};

const adminReadViews = {
  leaves: { title: '特殊申报', icon: FileText },
  records: { title: '考勤记录', icon: FileText },
  stats: { title: '考勤统计', icon: BarChart3 },
};

const adminLabels = {
  id: 'ID',
  name: '姓名',
  username: '账号',
  department: '院系',
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

function App() {
  const [session, setSession] = useState(() => {
    const raw = localStorage.getItem('qr-attendance-session');
    return raw ? JSON.parse(raw) : null;
  });
  const [teacherView, setTeacherView] = useState('courses');
  const [adminView, setAdminView] = useState('teachers');
  const [breadcrumb, setBreadcrumb] = useState(['首页', '我的课程']);
  const [collapsed, setCollapsed] = useState(false);

  function onLogin(nextSession) {
    localStorage.setItem('qr-attendance-session', JSON.stringify(nextSession));
    setSession(nextSession);
    setTeacherView('courses');
    setAdminView('teachers');
  }

  function logout() {
    localStorage.removeItem('qr-attendance-session');
    setSession(null);
    setTeacherView('courses');
    setAdminView('teachers');
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
              {Object.entries(adminResources).map(([key, config]) => {
                const Icon = config.icon;
                return (
                  <button key={key} className={adminView === key ? 'active' : ''} onClick={() => setAdminView(key)}>
                    <Icon size={17} />
                    <span>{config.title}</span>
                  </button>
                );
              })}
              {Object.entries(adminReadViews).map(([key, config]) => {
                const Icon = config.icon;
                return (
                  <button key={key} className={adminView === key ? 'active' : ''} onClick={() => setAdminView(key)}>
                    <Icon size={17} />
                    <span>{config.title}</span>
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
          <AdminPortal session={session} view={adminView} setBreadcrumb={setBreadcrumb} />
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

function AdminPortal({ session, view, setBreadcrumb }) {
  const client = useMemo(() => api.withToken(session.token), [session.token]);
  const config = adminResources[view];
  const readConfig = adminReadViews[view];
  const title = config?.title ?? readConfig?.title ?? '后台管理';

  useEffect(() => {
    setBreadcrumb(['首页', '后台管理', title]);
  }, [setBreadcrumb, title]);

  if (config) {
    return <AdminResourcePage client={client} config={config} />;
  }
  if (view === 'leaves') return <AdminLeavePage client={client} />;
  if (view === 'records') {
    return (
      <AdminReadonlyPage
        client={client}
        endpoint="/admin/attendance-records"
        title="考勤记录"
        subtitle="查看所有学生签到、请假和缺勤记录。"
        columns={['id', 'session_id', 'course_name', 'student_name', 'status', 'source', 'checked_in_at']}
      />
    );
  }
  return (
    <AdminReadonlyPage
      client={client}
      endpoint="/admin/attendance-stats"
      title="考勤统计"
      subtitle="按考勤场次聚合应到、已到、请假和缺勤。"
      columns={['session_id', 'course_name', 'teacher_name', 'class_name', 'total', 'present', 'excused', 'absent']}
    />
  );
}

function AdminResourcePage({ client, config }) {
  const [items, setItems] = useState([]);
  const [form, setForm] = useState(() => emptyAdminForm(config.fields));
  const [query, setQuery] = useState('');
  const [editId, setEditId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const filteredItems = useMemo(() => filterRows(items, query), [items, query]);

  useEffect(() => {
    setForm(emptyAdminForm(config.fields));
    setEditId(null);
    setQuery('');
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, config]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setItems(await client.get(config.endpoint));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    try {
      if (editId && !config.noEdit) {
        await client.put(`${config.endpoint}/${editId}`, normalizeAdminForm(form));
      } else {
        await client.post(config.endpoint, normalizeAdminForm(form));
      }
      setEditId(null);
      setForm(emptyAdminForm(config.fields));
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function remove(id) {
    setError('');
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
  }

  return (
    <div className="adminPage">
      <AdminPageHead title={config.title} subtitle="维护后台基础数据，支持搜索、刷新、编辑和删除。" icon={<config.icon />} count={items.length} onRefresh={load} />
      <section className="panel adminEditor">
        <form className="inlineForm" onSubmit={submit}>
          {config.fields.map(([name, label]) => (
            <label key={name}>
              {label}
              <input
                type={name === 'password' ? 'password' : 'text'}
                value={form[name] ?? ''}
                onChange={(event) => setForm({ ...form, [name]: event.target.value })}
              />
            </label>
          ))}
          <button type="submit">{editId && !config.noEdit ? <Save size={16} /> : <Plus size={16} />}{editId && !config.noEdit ? '保存' : '新增'}</button>
          {editId && !config.noEdit && <button type="button" className="ghost" onClick={() => { setEditId(null); setForm(emptyAdminForm(config.fields)); }}><X size={16} />取消</button>}
        </form>
        {error && <div className="error">{error}</div>}
      </section>
      <AdminTableToolbar query={query} onQuery={setQuery} />
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

function AdminReadonlyPage({ client, endpoint, title, subtitle, columns }) {
  const [items, setItems] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const filteredItems = useMemo(() => filterRows(items, query), [items, query]);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, endpoint]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setItems(await client.get(endpoint));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="adminPage">
      <AdminPageHead title={title} subtitle={subtitle} icon={<FileText />} count={items.length} onRefresh={load} />
      {error && <div className="error">{error}</div>}
      <AdminTableToolbar query={query} onQuery={setQuery} />
      <AdminDataTable rows={filteredItems} columns={columns} loading={loading} />
    </div>
  );
}

function AdminLeavePage({ client }) {
  const [items, setItems] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const filteredItems = useMemo(() => filterRows(items, query), [items, query]);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setItems(await client.get('/admin/leave-requests'));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function review(id, approved) {
    setError('');
    try {
      await client.post(`/admin/leave-requests/${id}/review`, { approved });
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="adminPage">
      <AdminPageHead title="特殊申报" subtitle="审核学生请假和特殊考勤申报。" icon={<FileText />} count={items.length} onRefresh={load} />
      {error && <div className="error">{error}</div>}
      <AdminTableToolbar query={query} onQuery={setQuery} />
      <section className="panel adminReviewList">
        {loading && <div className="empty">加载中</div>}
        {!loading && filteredItems.map((leave) => (
          <div className="review" key={leave.id}>
            <span>{leave.student_name} · {leave.reason}</span>
            <strong>{statusText(leave.status)}</strong>
            {leave.status === 'PENDING' && (
              <div className="actions">
                <button onClick={() => review(leave.id, true)}><Check size={16} />通过</button>
                <button className="ghost" onClick={() => review(leave.id, false)}>驳回</button>
              </div>
            )}
          </div>
        ))}
        {!loading && !filteredItems.length && <div className="empty">暂无数据</div>}
      </section>
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
        <span>{icon}{count} 条</span>
        <button className="ghost" onClick={onRefresh}><RefreshCw size={16} />刷新</button>
      </div>
    </section>
  );
}

function AdminTableToolbar({ query, onQuery }) {
  return (
    <section className="adminTableToolbar panel">
      <label className="searchField">
        搜索
        <span>
          <Search size={16} />
          <input placeholder="搜索当前表格" value={query} onChange={(event) => onQuery(event.target.value)} />
        </span>
      </label>
    </section>
  );
}

function AdminDataTable({ rows, columns, loading, resourceTitle = '', onEdit, onDelete }) {
  const hasActions = Boolean(onEdit || onDelete);
  return (
    <div className="panel adminTablePanel">
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
    </div>
  );
}

function emptyAdminForm(fields) {
  return Object.fromEntries(fields.map(([name]) => [name, name === 'password' ? 'password' : '']));
}

function normalizeAdminForm(form) {
  return Object.fromEntries(Object.entries(form).map(([key, value]) => {
    const numeric = ['classId', 'courseId', 'teacherId', 'assignmentId', 'studentId'].includes(key);
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
  };
  return String(row[name] ?? row[map[name]] ?? '');
}

function filterRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => Object.values(row).some((cell) => String(cell ?? '').toLowerCase().includes(value)));
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

function Header({ title, subtitle, icon }) {
  return (
    <header className="header">
      <div className="headerIcon">{icon}</div>
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
    </header>
  );
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
