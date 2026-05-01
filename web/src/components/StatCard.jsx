export function StatCard({ icon, value, label }) {
  return (
    <div className="statCard">
      <span className="statIcon">{icon}</span>
      <div>
        <strong>{value}</strong>
        <p>{label}</p>
      </div>
    </div>
  );
}
