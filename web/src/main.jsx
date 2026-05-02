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
  const { sortedRows: sortedCourses, sortColumn: courseSortColumn, sortDirection: courseSortDirection, onSort: handleCourseSort } = useTableSort(filteredCourses);
  const totalPages = Math.max(1, Math.ceil(sortedCourses.length / pageSize));
  const pageCourses = sortedCourses.slice((page - 1) * pageSize, page * pageSize);

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
                <SortableTableHeader label="课程名称" column="name" sortColumn={courseSortColumn} sortDirection={courseSortDirection} onSort={handleCourseSort} />
                <SortableTableHeader label="课程代码" column="code" sortColumn={courseSortColumn} sortDirection={courseSortDirection} onSort={handleCourseSort} />
                <SortableTableHeader label="院系" column="department_name" sortColumn={courseSortColumn} sortDirection={courseSortDirection} onSort={handleCourseSort} />
                <SortableTableHeader label="授课教师" column="teacher_name" sortColumn={courseSortColumn} sortDirection={courseSortDirection} onSort={handleCourseSort} />
                <SortableTableHeader label="学期" column="term" sortColumn={courseSortColumn} sortDirection={courseSortDirection} onSort={handleCourseSort} />
                <SortableTableHeader label="选课人数" column="student_count" sortColumn={courseSortColumn} sortDirection={courseSortDirection} onSort={handleCourseSort} />
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
          total={sortedCourses.length}
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

  const { sortedRows: sortedDepartments, sortColumn: deptSortColumn, sortDirection: deptSortDirection, onSort: handleDeptSort } = useTableSort(filteredDepartments);

  const totalPages = Math.max(1, Math.ceil(sortedDepartments.length / pageSize));
  const pageDepartments = sortedDepartments.slice((page - 1) * pageSize, page * pageSize);
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
                <SortableTableHeader label="院系名称" column="name" sortColumn={deptSortColumn} sortDirection={deptSortDirection} onSort={handleDeptSort} />
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
          total={sortedDepartments.length}
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

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

export default App;
