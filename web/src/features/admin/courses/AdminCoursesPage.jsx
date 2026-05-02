import { useEffect, useMemo, useState } from 'react';
import { AdminPageHead } from '../../../components/AdminPageHead';
import { AdminPagination } from '../../../components/AdminPagination';
import { SortableTableHeader } from '../../../components/SortableTableHeader';
import { useTableSort } from '../../../hooks/useTableSort';
import { normalizeAdminForm } from '../../../utils/adminForm';
import { filterAdminCourses } from '../../../utils/filters';
import { AdminCourseDetail } from './AdminCourseDetail';
import { CourseFormDialog } from './CourseFormDialog';
import { CourseTableHeader } from './CourseTableHeader';

export function AdminCoursesPage({ client }) {
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
