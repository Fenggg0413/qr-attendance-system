export function CourseAttendanceTable({ rows }) {
  return (
    <section className="panel">
      <div className="panelHead">
        <h2>课程出勤情况</h2>
        {rows.length > 0 && <span className="panelCount">共 {rows.length} 门课程</span>}
      </div>
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
