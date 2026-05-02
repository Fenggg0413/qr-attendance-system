import { Plus } from 'lucide-react';
import { courseNameText, formatSeconds } from '../../utils/format';

export function TodayCard({ card, now, busy, onStart, onView }) {
  const startMs = new Date(card.startTime).getTime();
  const endMs = new Date(card.endTime).getTime();
  const periodLabel = card.periodEnd > card.periodStart ? `第 ${card.periodStart}-${card.periodEnd} 节` : `第 ${card.periodStart} 节`;
  const startLabel = new Date(card.startTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
  const endLabel = new Date(card.endTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });

  let statusPill = null;
  let cta = null;
  if (card.phase === 'BEFORE') {
    const seconds = Math.max(0, Math.floor((startMs - now) / 1000));
    statusPill = <span className="pill">未到上课时间</span>;
    cta = <button className="ghost" disabled>距上课 {formatSeconds(seconds)}</button>;
  } else if (card.phase === 'RUNNING') {
    const seconds = Math.max(0, Math.floor((endMs - now) / 1000));
    statusPill = <span className="pill live">进行中 · 剩 {formatSeconds(seconds)}</span>;
    cta = <button onClick={onView}>查看二维码</button>;
  } else if (card.phase === 'ENDED') {
    statusPill = <span className="pill">已结束</span>;
    cta = card.session ? <button className="ghost" onClick={onView}>查看记录</button> : <button className="ghost" disabled>未发起</button>;
  } else {
    statusPill = <span className="pill live">可发起</span>;
    cta = <button disabled={busy} onClick={onStart}><Plus size={16} />开始考勤</button>;
  }

  return (
    <article className="courseCard">
      <div>
        <span className="eyebrow">{periodLabel} · {startLabel}-{endLabel}</span>
        <h2>{courseNameText(card.courseName)}</h2>
        <p>{[card.className, card.classroomName].filter(Boolean).join(' · ') || '—'}</p>
        <div style={{ marginTop: 8 }}>{statusPill}</div>
        {card.session && (
          <p className="hint" style={{ marginTop: 8 }}>
            {card.session.kind === 'MAKEUP' ? '补考勤 · ' : ''}
            已签到 {Number(card.session.presentCount ?? 0)} / {Number(card.session.totalCount ?? 0)}
          </p>
        )}
      </div>
      <div className="cardActions">{cta}</div>
    </article>
  );
}
