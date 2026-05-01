import { useEffect, useState } from 'react';
import { X } from 'lucide-react';

export function MakeupDialog({ client, courses, onClose, onCreated }) {
  const [courseId, setCourseId] = useState(courses[0]?.id ?? '');
  const [slots, setSlots] = useState([]);
  const [slotId, setSlotId] = useState('');
  const [reason, setReason] = useState('');
  const [duration, setDuration] = useState(30);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!courseId) {
      setSlots([]);
      return;
    }
    let cancelled = false;
    client.get(`/teacher/courses/${courseId}`).then((data) => {
      if (cancelled) return;
      const list = Array.isArray(data?.scheduleSlots) ? data.scheduleSlots : [];
      setSlots(list);
      setSlotId(list[0]?.id ?? '');
    }).catch((err) => {
      if (!cancelled) setError(err.message ?? '加载排课失败');
    });
    return () => {
      cancelled = true;
    };
  }, [client, courseId]);

  async function submit(event) {
    event.preventDefault();
    setError('');
    if (!slotId) {
      setError('请选择目标排课');
      return;
    }
    if (!reason.trim()) {
      setError('请填写补考勤理由');
      return;
    }
    setBusy(true);
    try {
      const created = await client.post('/teacher/attendance-sessions/makeup', {
        slotId: Number(slotId),
        reason: reason.trim(),
        durationMinutes: Number(duration),
      });
      const slot = slots.find((s) => Number(s.id) === Number(slotId));
      const card = {
        courseName: courses.find((c) => Number(c.id) === Number(courseId))?.name,
        weekday: slot?.weekday ?? '',
        period: slot?.period ?? '',
        classroom_name: slot?.classroom_name ?? '',
      };
      await onCreated(created, card);
    } catch (err) {
      setError(err.message ?? '补考勤失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modalLayer">
      <section className="modal" role="dialog" aria-modal="true" aria-label="补考勤">
        <div className="modalHead">
          <div>
            <h2>补考勤</h2>
            <p>用于调课、临时改时间等特殊情况</p>
          </div>
          <button className="iconButton" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>
        <form className="modalScroll" onSubmit={submit}>
          <div className="modalBody">
            <label>
              课程
              <select value={courseId} onChange={(event) => setCourseId(Number(event.target.value))}>
                {courses.map((c) => <option key={c.id} value={c.id}>{c.name}（{c.code}）</option>)}
              </select>
            </label>
            <label>
              目标排课
              <select value={slotId} onChange={(event) => setSlotId(Number(event.target.value))} disabled={!slots.length}>
                {!slots.length && <option value="">该课程暂无排课</option>}
                {slots.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.weekday} 第 {s.period} 节{s.classroom_name ? ` · ${s.classroom_name}` : ''}
                  </option>
                ))}
              </select>
            </label>
            <label>
              开放时长（分钟）
              <input type="number" min="1" max="120" value={duration} onChange={(event) => setDuration(Number(event.target.value))} />
            </label>
            <label>
              理由
              <textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="例如：周一调到本日补课" />
            </label>
            {error && <div className="error">{error}</div>}
            <div className="actions right">
              <button type="button" className="ghost" onClick={onClose}>取消</button>
              <button type="submit" disabled={busy || !slotId}>提交补考勤</button>
            </div>
          </div>
        </form>
      </section>
    </div>
  );
}
