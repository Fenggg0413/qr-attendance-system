import { useEffect, useState } from 'react';
import { Check, ClipboardCheck, X } from 'lucide-react';
import { formatDate, statusText } from '../../utils/format';

export function TeacherLeaveRequests({ client, onAuthExpired }) {
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [actingId, setActingId] = useState(null);
  const [toast, setToast] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    client.get(`/teacher/leave-requests?status=${statusFilter}`)
      .then((data) => {
        if (cancelled) return;
        setItems(Array.isArray(data) ? data : []);
        setError('');
      })
      .catch((err) => {
        if (cancelled) return;
        if (err.status === 401) {
          onAuthExpired();
          return;
        }
        setError(err.message ?? '加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, statusFilter, onAuthExpired]);

  async function review(id, approved) {
    setActingId(id);
    setError('');
    try {
      await client.post(`/teacher/leave-requests/${id}/review`, { approved });
      setToast(approved ? '已通过该申报' : '已驳回该申报');
      const data = await client.get(`/teacher/leave-requests?status=${statusFilter}`);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message ?? '审核失败');
    } finally {
      setActingId(null);
      setTimeout(() => setToast(''), 2400);
    }
  }

  const filters = [
    ['PENDING', '待审核'],
    ['APPROVED', '已通过'],
    ['REJECTED', '已驳回'],
    ['ALL', '全部'],
  ];

  const pendingCount = items.filter((row) => row.status === 'PENDING').length;

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>申报审核</h1>
          <p className="muted">审核学生提交的请假/特殊情况申报，通过后将自动登记为「已请假」考勤。</p>
        </div>
        <div className="summaryStrip">
          <span><ClipboardCheck size={16} />当前列表 {items.length}{statusFilter === 'PENDING' ? `（待审核 ${pendingCount}）` : ''}</span>
        </div>
      </section>

      <section className="panel">
        <div className="leaveFilterStrip" role="tablist" aria-label="状态筛选">
          {filters.map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={statusFilter === value}
              className={statusFilter === value ? 'pill live' : 'pill'}
              onClick={() => setStatusFilter(value)}
            >
              {label}
            </button>
          ))}
        </div>
        {error && <div className="error">{error}</div>}
        {toast && <div className="leaveToast" role="status">{toast}</div>}
        <div className="tableWrap">
          <table className="adminTable">
            <thead>
              <tr>
                <th>学生</th>
                <th>课程</th>
                <th>考勤时间</th>
                <th>申报原因</th>
                <th>状态</th>
                <th>审核记录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7}>加载中</td></tr>}
              {!loading && items.length === 0 && (
                <tr><td colSpan={7}>暂无申报</td></tr>
              )}
              {!loading && items.map((row) => (
                <tr key={row.id}>
                  <td>
                    <div>{row.student_name}</div>
                    <small className="muted">{row.student_no}</small>
                  </td>
                  <td>
                    <div>{row.course_name}</div>
                    <small className="muted">{row.course_code}</small>
                  </td>
                  <td>{formatDate(row.session_started_at)}</td>
                  <td>{row.reason}</td>
                  <td><span className={`statusBadge ${String(row.status).toLowerCase()}`}>{statusText(row.status)}</span></td>
                  <td>
                    {row.status === 'PENDING' ? (
                      <span className="muted">—</span>
                    ) : (
                      <div>
                        <div>{row.reviewer_name ?? '-'}</div>
                        <small className="muted">{formatDate(row.reviewed_at)}</small>
                      </div>
                    )}
                  </td>
                  <td>
                    {row.status === 'PENDING' ? (
                      <div className="actions">
                        <button
                          type="button"
                          className="ghost"
                          disabled={actingId === row.id}
                          onClick={() => review(row.id, true)}
                          aria-label={`通过 ${row.student_name} 的申报`}
                        >
                          <Check size={15} />通过
                        </button>
                        <button
                          type="button"
                          className="ghost"
                          disabled={actingId === row.id}
                          onClick={() => review(row.id, false)}
                          aria-label={`驳回 ${row.student_name} 的申报`}
                        >
                          <X size={15} />驳回
                        </button>
                      </div>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
