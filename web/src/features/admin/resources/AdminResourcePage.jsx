import { useEffect, useMemo, useState } from 'react';
import { Plus, Save, X } from 'lucide-react';
import { AdminDataTable } from '../../../components/AdminDataTable';
import { AdminPageHead } from '../../../components/AdminPageHead';
import { AdminPagination } from '../../../components/AdminPagination';
import { AdminResourceFormDialog } from '../../../components/AdminResourceFormDialog';
import { AdminTableToolbar } from '../../../components/AdminTableToolbar';
import { useTableSort } from '../../../hooks/useTableSort';
import { adminFieldValue, emptyAdminForm, normalizeAdminForm } from '../../../utils/adminForm';
import { filterClassroomRows, filterRows, filterStudentRows, filterTeacherRows } from '../../../utils/filters';
import { ClassroomFormDialog } from '../classrooms/ClassroomFormDialog';
import { ClassroomTableHeader } from './ClassroomTableHeader';
import { StudentTableHeader } from './StudentTableHeader';
import { TeacherTableHeader } from './TeacherTableHeader';

export function AdminResourcePage({ client, config }) {
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
  const { sortedRows: sortedItems, sortColumn, sortDirection, onSort, resetSort } = useTableSort(filteredItems);
  const studentTotalPages = Math.max(1, Math.ceil(sortedItems.length / studentPageSize));
  const teacherTotalPages = Math.max(1, Math.ceil(sortedItems.length / teacherPageSize));
  const classroomTotalPages = Math.max(1, Math.ceil(sortedItems.length / classroomPageSize));
  const studentRows = isStudentResource
    ? sortedItems.slice((studentPage - 1) * studentPageSize, studentPage * studentPageSize)
    : sortedItems;
  const displayedStudentRows = isStudentResource
    ? studentRows.map((row, index) => ({ ...row, display_index: (studentPage - 1) * studentPageSize + index + 1 }))
    : studentRows;
  const teacherRows = isTeacherResource
    ? sortedItems.slice((teacherPage - 1) * teacherPageSize, teacherPage * teacherPageSize)
    : sortedItems;
  const displayedTeacherRows = isTeacherResource
    ? teacherRows.map((row, index) => ({ ...row, display_index: (teacherPage - 1) * teacherPageSize + index + 1 }))
    : teacherRows;
  const classroomRows = isClassroomResource
    ? sortedItems.slice((classroomPage - 1) * classroomPageSize, classroomPage * classroomPageSize)
    : sortedItems;
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
    resetSort();
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
          sortColumn={sortColumn}
          sortDirection={sortDirection}
          onSort={onSort}
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
          sortColumn={sortColumn}
          sortDirection={sortDirection}
          onSort={onSort}
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
          sortColumn={sortColumn}
          sortDirection={sortDirection}
          onSort={onSort}
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
        rows={sortedItems.map((row, index) => ({ ...row, display_index: index + 1 }))}
        columns={config.columns}
        labels={config.labels}
        loading={loading}
        resourceTitle={config.title.replace(/管理$/, '')}
        onEdit={config.noEdit ? null : edit}
        onDelete={remove}
        sortColumn={sortColumn}
        sortDirection={sortDirection}
        onSort={onSort}
      />
    </div>
  );
}
