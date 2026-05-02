import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, Check, ClipboardCheck, Plus } from 'lucide-react';
import { courseNameText, formatToday } from '../../utils/format';
import { MakeupDialog } from './MakeupDialog';
import { TodayCard } from './TodayCard';

export function TodayBoard({ client, courses, onLive }) {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [now, setNow] = useState(() => Date.now());
  const [makeupOpen, setMakeupOpen] = useState(false);

  const refresh = async () => {
    try {
      const next = await client.get('/teacher/today');
      setItems(Array.isArray(next) ? next : []);
      setError('');
    } catch (err) {
      setError(err.message ?? '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, 30000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const summary = useMemo(() => {
    const total = items.length;
    const running = items.filter((it) => it.phase === 'RUNNING').length;
    const ended = items.filter((it) => it.phase === 'ENDED').length;
    return { total, running, ended, pending: total - running - ended };
  }, [items]);

  async function startSession(card) {
    setBusyId(card.slotId);
    try {
      const created = await client.post(`/teacher/schedule-slots/${card.slotId}/attendance-sessions`, { method: 'QR' });
      const headline = `${courseNameText(card.courseName)} · 第 ${card.periodStart}${card.periodEnd > card.periodStart ? `-${card.periodEnd}` : ''} 节`;
      const subline = `${card.className || ''}${card.classroomName ? ` · ${card.classroomName}` : ''}`;
      onLive({ id: created.id, endsAt: created.endsAt ?? created.ends_at, headline, subline });
      await refresh();
    } catch (err) {
      setError(err.message ?? '发起考勤失败');
    } finally {
      setBusyId(null);
    }
  }

  function viewSession(card) {
    if (!card.session) return;
    const headline = `${courseNameText(card.courseName)} · 第 ${card.periodStart}${card.periodEnd > card.periodStart ? `-${card.periodEnd}` : ''} 节`;
    const subline = `${card.className || ''}${card.classroomName ? ` · ${card.classroomName}` : ''}`;
    onLive({
      id: card.session.id,
      endsAt: card.session.ends_at ?? card.session.endsAt,
      status: card.session.status,
      recordsOnly: card.phase === 'ENDED',
      headline,
      subline,
    });
  }

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>今日课表</h1>
          <p>{formatToday()}</p>
        </div>
        <div className="summaryStrip">
          <span><CalendarDays size={16} />共 {summary.total} 节</span>
          <span><ClipboardCheck size={16} />进行中 {summary.running}</span>
          <span><Check size={16} />已结束 {summary.ended}</span>
          <button className="ghost" onClick={() => setMakeupOpen(true)}><Plus size={16} />补考勤</button>
        </div>
      </section>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <div className="panel hint">加载中</div>
      ) : !items.length ? (
        <div className="empty panel">今天没有排课，可使用「补考勤」记录调课。</div>
      ) : (
        <section className="courseCards">
          {items.map((card) => (
            <TodayCard
              key={card.slotId}
              card={card}
              now={now}
              busy={busyId === card.slotId}
              onStart={() => startSession(card)}
              onView={() => viewSession(card)}
            />
          ))}
        </section>
      )}

      {makeupOpen && (
        <MakeupDialog
          client={client}
          courses={courses}
          onClose={() => setMakeupOpen(false)}
          onCreated={async (created, card) => {
            setMakeupOpen(false);
            onLive({
              id: created.id,
              endsAt: created.endsAt ?? created.ends_at,
              headline: `${courseNameText(card.courseName)} · 补考勤`,
              subline: `${card.weekday} 第 ${card.period} 节${card.classroom_name ? ` · ${card.classroom_name}` : ''}`,
            });
            await refresh();
          }}
        />
      )}
    </div>
  );
}
