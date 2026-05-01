export function roleText(role) {
  return { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }[role] ?? '未知角色';
}

export function statusText(status) {
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

export function sourceText(source) {
  return { QR: '扫码', CODE: '验证码', MANUAL: '手动', LEAVE: '申报' }[source] ?? '-';
}

export function methodText(method) {
  return { QR: '二维码', CODE: '数字验证码', MANUAL: '手动点名' }[method] ?? '二维码';
}

export function courseNameText(name) {
  return name ?? '-';
}

export function teacherOptionText(teacher) {
  return `${teacher.name}（${teacher.username ?? `ID ${teacher.id}`}） · ${teacher.department_name ?? teacher.department ?? '未设置院系'}`;
}

export function formatDate(value) {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

export function formatCourseSchedule(course) {
  if (!course.weekday && !course.start_time && !course.end_time) return '未排课';
  const time = [course.start_time, course.end_time].filter(Boolean).join('-');
  return [course.weekday, time].filter(Boolean).join(' ');
}

export function formatToday() {
  return new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  });
}

export function formatSeconds(value) {
  const minutes = Math.floor(value / 60);
  const seconds = value % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

export function displayAdminRow(row) {
  return courseNameText(row.name || row.course_name || row.student_name || row.username || `#${row.id}`);
}

export function formatAdminCell(row, column) {
  if (column === 'status') return statusText(row[column]);
  if (column === 'source') return sourceText(row[column]);
  if (column === 'course_name' || column === 'name') return courseNameText(row[column]);
  return String(row[column] ?? '');
}

export function sortableAriaSort(column, sortColumn, sortDirection) {
  if (sortColumn !== column) return 'none';
  return sortDirection === 'asc' ? 'ascending' : 'descending';
}
