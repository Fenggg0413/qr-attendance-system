import { X } from 'lucide-react';
import { formatDate, methodText } from '../../utils/format';
import { RecordsTable } from './RecordsTable';

export function RecordsDrawer({ session, records, onClose }) {
  return (
    <div className="drawerLayer">
      <aside className="drawer" role="dialog" aria-modal="true" aria-label={`考勤明细 ${session.id}`}>
        <div className="modalHead">
          <div>
            <h2>考勤明细</h2>
            <p>{formatDate(session.started_at)} · {methodText(session.method)}</p>
          </div>
          <button className="iconButton" onClick={onClose} aria-label="关闭明细"><X size={18} /></button>
        </div>
        <div className="modalScroll">
          <RecordsTable records={records} />
        </div>
      </aside>
    </div>
  );
}
