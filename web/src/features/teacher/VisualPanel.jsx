import { BarChart3 } from 'lucide-react';

export function VisualPanel({ sessions, students }) {
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
