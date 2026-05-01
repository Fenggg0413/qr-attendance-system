import { Trash2 } from 'lucide-react';
import { formatDate, methodText, statusText } from '../../utils/format';

export function SessionsTable({ sessions, onOpenRecords, onDelete }) {
  if (!sessions.length) return <div className="empty">暂无考勤记录</div>;
  return (
    <div className="tableWrap">
      <table>
        <thead>
          <tr>
            <th>开始时间</th>
            <th>方式</th>
            <th>状态</th>
            <th>已到</th>
            <th>缺勤</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((row) => (
            <tr key={row.id}>
              <td>{formatDate(row.started_at)}</td>
              <td>{methodText(row.method)}</td>
              <td><span className={`pill ${row.status === 'OPEN' ? 'live' : ''}`}>{statusText(row.status)}</span></td>
              <td>{row.present_count ?? 0}</td>
              <td>{row.absent_count ?? 0}</td>
              <td>
                <div className="actions">
                  <button className="ghost" onClick={() => onOpenRecords(row)}>查看明细</button>
                  {onDelete && (
                    <button className="ghost danger" onClick={() => { if (window.confirm('确定删除该考勤记录？删除后不可恢复。')) onDelete(row.id); }}>
                    <Trash2 size={14} />删除
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
