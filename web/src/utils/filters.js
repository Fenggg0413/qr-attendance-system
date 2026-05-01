export function filterRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => Object.values(row).some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

export function filterStudentRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => [row.name, row.student_no].some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

export function filterTeacherRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => [row.name, row.username].some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

export function filterClassroomRows(rows, query) {
  const value = query.trim().toLowerCase();
  if (!value) return rows;
  return rows.filter((row) => [row.name, row.building].some((cell) => String(cell ?? '').toLowerCase().includes(value)));
}

export function filterAdminCourses(courses, filters) {
  const query = filters.query.trim().toLowerCase();
  return courses.filter((course) => {
    const matchesQuery = !query || [course.name, course.code].some((cell) => String(cell ?? '').toLowerCase().includes(query));
    const matchesDepartment = filters.department === '全部院系' || course.department_name === filters.department;
    const matchesTerm = filters.term === '全部学期' || course.term === filters.term;
    return matchesQuery && matchesDepartment && matchesTerm;
  });
}

export function sortRows(rows, column, direction) {
  if (!column || !direction) return rows;
  return [...rows].sort((a, b) => {
    const aVal = a[column] ?? '';
    const bVal = b[column] ?? '';
    const aText = String(aVal).trim();
    const bText = String(bVal).trim();
    const aNum = Number(aText);
    const bNum = Number(bText);
    if (aText !== '' && bText !== '' && Number.isFinite(aNum) && Number.isFinite(bNum)) {
      return direction === 'asc' ? aNum - bNum : bNum - aNum;
    }
    const cmp = String(aVal).localeCompare(String(bVal), 'zh-Hans-CN');
    return direction === 'asc' ? cmp : -cmp;
  });
}

export function handleColumnSort(column, currentColumn, currentDirection) {
  if (column === currentColumn) {
    if (currentDirection === 'asc') return { sortColumn: column, sortDirection: 'desc' };
    return { sortColumn: null, sortDirection: null };
  }
  return { sortColumn: column, sortDirection: 'asc' };
}

export function sessionTotals(sessions) {
  const validSessions = sessions.filter((row) => Number(row.total_count ?? 0) > 0);
  if (!validSessions.length) {
    return { attendanceRate: '0%', absenceRate: '0%' };
  }

  const rates = validSessions.reduce(
    (sum, row) => {
      const total = Number(row.total_count ?? 0);
      const attended = Number(row.present_count ?? 0) + Number(row.late_count ?? 0);
      return {
        attendance: sum.attendance + attended / total,
        absence: sum.absence + Number(row.absent_count ?? 0) / total,
      };
    },
    { attendance: 0, absence: 0 },
  );
  return {
    attendanceRate: `${Math.round((rates.attendance / validSessions.length) * 100)}%`,
    absenceRate: `${Math.round((rates.absence / validSessions.length) * 100)}%`,
  };
}
