import { sourceText, statusText } from '../../utils/format';

export function RecordsTable({ records, ended = true }) {
  function pillClass(status) {
    if (status === 'PRESENT' || status === 'LATE') return 'pill present';
    if (status === 'ABSENT') return ended ? 'pill danger' : 'pill neutral';
    return 'pill';
  }
  return (
    <div className="tableWrap compactTable">
      <table>
        <thead>
          <tr>
            <th>学生</th>
            <th>学号</th>
            <th>状态</th>
            <th>来源</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr key={`${record.student_id}-${record.id ?? 'absent'}`}>
              <td>{record.student_name}</td>
              <td>{record.student_no}</td>
              <td><span className={pillClass(record.status)}>{status === 'ABSENT' && !ended ? '未签到' : statusText(record.status)}</span></td>
              <td>{sourceText(record.source)}</td>
              <td>{record.checked_in_at ? new Date(record.checked_in_at).toLocaleTimeString() : '-'}</td>
            </tr>
          ))}
          {!records.length && (
            <tr>
              <td colSpan="5">暂无签到记录</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
