export function DonutSummary({ distribution }) {
  const present = Number(distribution.present ?? 0);
  const absent = Number(distribution.absent ?? 0);
  const late = Number(distribution.late ?? 0);
  const total = Math.max(1, present + absent + late);
  const presentRate = Math.round((present / total) * 100);
  const lateRate = Math.round((late / total) * 100);
  const absentRate = Math.round((absent / total) * 100);
  const presentEnd = (present / total) * 360;
  const lateEnd = presentEnd + (late / total) * 360;
  return (
    <div className="donutSummary">
      <div
        className="donut"
        style={{ background: `conic-gradient(#16a34a 0 ${presentEnd}deg, #f59e0b ${presentEnd}deg ${lateEnd}deg, #ef4444 ${lateEnd}deg 360deg)` }}
      >
        <span>{distribution.rate ?? 0}%</span>
      </div>
      <div className="legendList">
        <span><i className="present" />出勤率 {presentRate}%</span>
        <span><i className="late" />迟到率 {lateRate}%</span>
        <span><i className="absent" />缺勤率 {absentRate}%</span>
      </div>
    </div>
  );
}
