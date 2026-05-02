import React, { useEffect, useMemo, useState } from 'react';
import { CalendarDays, ChevronLeft, Plus, Save, X } from 'lucide-react';
import { fallbackTerms, schedulePeriods, scheduleWeekDays } from '../../../constants/schedule';
import { normalizeAdminForm } from '../../../utils/adminForm';
import { ScheduleSlotDialog } from './ScheduleSlotDialog';
import { StudentPickerDialog } from './StudentPickerDialog';
import { TeacherCombobox } from './TeacherCombobox';

export function AdminCourseDetail({ client, detail, onBack, onChanged }) {
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
