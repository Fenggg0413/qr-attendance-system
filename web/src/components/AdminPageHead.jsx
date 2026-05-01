import { RefreshCw } from 'lucide-react';

export function AdminPageHead({ title, subtitle, icon, count, onRefresh }) {
  return (
    <section className="pageHead adminHead">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      <div className="summaryStrip">
        {count !== undefined && <span>{icon}{count} 条</span>}
        <button className="ghost" onClick={onRefresh}><RefreshCw size={16} />刷新</button>
      </div>
    </section>
  );
}
