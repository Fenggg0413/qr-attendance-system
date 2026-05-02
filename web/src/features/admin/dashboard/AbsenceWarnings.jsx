export function AbsenceWarnings({ rows }) {
  return (
    <section className="panel">
      <div className="panelHead">
        <h2>预警提醒</h2>
        {rows.length > 0 && <span className="panelCount">共 {rows.length} 条预警</span>}
      </div>
      <div className="warningList">
        {rows.map((row) => (
          <div className="warningItem" key={row.student_id ?? row.student_no ?? row.student_name}>
            <span className="avatarMini warningAvatar">{String(row.student_name ?? '学').slice(0, 1)}</span>
            <strong>{row.student_name}</strong>
            <span>{row.student_no ?? '未设置学号'}</span>
            <span className="warningCount">缺勤 {Number(row.absent_count ?? 0)} 次</span>
          </div>
        ))}
        {!rows.length && <div className="empty">暂无预警</div>}
      </div>
    </section>
  );
}
