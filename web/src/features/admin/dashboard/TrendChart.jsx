export function TrendChart({ rows }) {
  const max = Math.max(1, ...rows.map((row) => Number(row.present ?? 0) + Number(row.absent ?? 0) + Number(row.late ?? 0)));
  return (
    <div className="adminTrendChart">
      {rows.map((row) => {
        const present = Number(row.present ?? 0);
        const absent = Number(row.absent ?? 0);
        const late = Number(row.late ?? 0);
        return (
          <div className="adminTrendColumn" key={row.date}>
            <div className="stackBar" style={{ height: `${Math.max(8, ((present + absent + late) / max) * 100)}%` }}>
              <i className="present" style={{ flex: present }} />
              <i className="late" style={{ flex: late }} />
              <i className="absent" style={{ flex: absent }} />
            </div>
            <span>{String(row.date ?? '').slice(5)}</span>
          </div>
        );
      })}
    </div>
  );
}
