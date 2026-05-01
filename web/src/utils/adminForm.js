export function emptyAdminForm(fields) {
  return Object.fromEntries(fields.map(([name]) => [name, '']));
}

export function normalizeAdminForm(form) {
  return Object.fromEntries(Object.entries(form).map(([key, value]) => {
    const numeric = ['classId', 'courseId', 'teacherId', 'assignmentId', 'studentId', 'departmentId'].includes(key);
    return [key, numeric && value !== '' ? Number(value) : value];
  }));
}

export function adminFieldValue(row, name) {
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
