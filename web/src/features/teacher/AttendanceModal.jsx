import { useEffect, useState } from 'react';
import { Check, Minimize2, X } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { ApiError } from '../../services/api';
import { formatSeconds } from '../../utils/format';
import { RecordsTable } from './RecordsTable';

export function AttendanceModal({ client, session, headline, subline, onClose, onClosed }) {
  const [qr, setQr] = useState(null);
  const [records, setRecords] = useState([]);
  const [error, setError] = useState('');
  const [ended, setEnded] = useState(() => session?.recordsOnly || session?.status === 'CLOSED');
  const [remaining, setRemaining] = useState(0);
  const [qrRefresh, setQrRefresh] = useState(0);
  const [fullscreen, setFullscreen] = useState(false);

  const presentCount = records.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length;
  const totalCount = records.length;
  const progressPct = totalCount ? Math.round((presentCount / totalCount) * 100) : 0;

  useEffect(() => {
    if (!session) return undefined;
    let cancelled = false;
    async function refresh() {
      try {
        const shouldLoadQr = !session.recordsOnly && !ended;
        const qrRequest = shouldLoadQr
          ? client.get(`/teacher/attendance-sessions/${session.id}/qr`).catch((err) => {
            if (err instanceof ApiError && err.status === 410) {
              return null;
            }
            throw err;
          })
          : Promise.resolve(null);
        const [nextQr, nextRecords] = await Promise.all([
          qrRequest,
          client.get(`/teacher/attendance-sessions/${session.id}/records`),
        ]);
        if (!cancelled) {
          if (nextQr) setQr(nextQr);
          else if (!shouldLoadQr) setQr(null);
          setRecords(nextRecords);
          setError('');
          if (!nextQr && shouldLoadQr) setEnded(true);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message ?? '刷新失败');
          if (err instanceof ApiError && err.status === 410) setEnded(true);
        }
      }
    }
    refresh();
    const timer = session.recordsOnly || ended ? null : window.setInterval(refresh, 5000);
    return () => {
      cancelled = true;
      if (timer) window.clearInterval(timer);
    };
  }, [session, client, ended]);

  useEffect(() => {
    if (!session || ended) return undefined;
    function tick() {
      const next = Math.max(0, Math.floor((new Date(session.endsAt ?? session.ends_at).getTime() - Date.now()) / 1000));
      setRemaining(next);
      if (next === 0) setEnded(true);
    }
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [session, ended]);

  useEffect(() => {
    if (!qr?.expiresAt) return undefined;
    function tick() {
      const next = Math.max(0, Math.floor((new Date(qr.expiresAt).getTime() - Date.now()) / 1000));
      setQrRefresh(next);
    }
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [qr?.expiresAt]);

  async function closeSession() {
    if (!session) return;
    if (!window.confirm('确定提前结束考勤？二维码将立即失效，未签到学生将记为缺勤。')) return;
    await client.post(`/teacher/attendance-sessions/${session.id}/close`, {});
    setEnded(true);
    if (onClosed) await onClosed();
  }

  if (fullscreen && qr?.payload) {
    return (
      <div className="qrFullscreen">
        <div className="qrFullscreenInner">
          <div className="qrFullscreenCountdown">
            {ended ? '考勤已结束' : formatSeconds(remaining)}
          </div>
          {!ended && <div className="qrFullscreenRefresh">{qrRefresh > 0 ? `刷新倒计时 ${qrRefresh}s` : '刷新中…'}</div>}
          <div className="qrQuietZone">
            <QRCodeSVG value={qr.payload} size={Math.min(520, typeof window !== 'undefined' ? window.innerWidth * 0.6 : 400, typeof window !== 'undefined' ? window.innerHeight * 0.45 : 400)} level="M" />
          </div>
          <div className="qrFullscreenStats">
            已签到 {presentCount} / {totalCount} 人
          </div>
        </div>
        <button className="qrFullscreenExit" onClick={() => setFullscreen(false)}>
          <Minimize2 size={18} />退出全屏
        </button>
      </div>
    );
  }

  return (
    <div className="modalLayer">
      <section className="modal" role="dialog" aria-modal="true" aria-label="动态考勤">
        <div className="modalHead">
          <div>
            <h2>{headline ?? '动态考勤'}</h2>
            {subline && <p>{subline}</p>}
          </div>
          <button className="iconButton" onClick={onClose} aria-label="隐藏窗口，考勤继续" title="隐藏窗口，考勤在后台继续"><X size={18} /></button>
        </div>

        <div className="modalScroll">
          <div className="modalBody attendanceLive">
            <div className="liveHeader">
              <h3>动态签到码</h3>
              <span className={ended ? 'pill' : 'pill live'}>{ended ? '考勤已结束' : `剩余 ${formatSeconds(remaining)}`}</span>
            </div>
            <div className="qrPanel">
              <div
                className={`qrPanelTop${!ended && qr?.payload ? ' qrClickable' : ''}`}
                onClick={!ended && qr?.payload ? () => setFullscreen(true) : undefined}
                title={!ended && qr?.payload ? '点击全屏展示二维码' : undefined}
              >
                {qr?.payload && <div className="qrQuietZone qrQuietZoneSm"><QRCodeSVG value={qr.payload} size={190} /></div>}
                {!ended && qr?.payload && (
                  <div className="qrHoverOverlay">
                    <span>点击放大</span>
                  </div>
                )}
              </div>
              {!ended && qr?.expiresAt && <p className="hint">{qrRefresh > 0 ? `刷新倒计时 ${qrRefresh}s` : '刷新中…'}</p>}
              {error && <div className="error">{error}</div>}
            </div>
            <div className="liveRight">
              <div className="liveStats">
                <div className="liveStatsText">
                  <Check size={16} />
                  <span>已签到 <strong>{presentCount}</strong> / {totalCount} 人</span>
                  <em>{progressPct}%</em>
                </div>
                <div className="progressBar"><i style={{ width: `${progressPct}%` }} /></div>
              </div>
              <div className="liveScrollArea">
                <RecordsTable records={records} ended={ended} />
              </div>
            </div>
          </div>
        </div>
        <div className="modalFooter">
          <button className="danger" disabled={ended} onClick={closeSession}>
            提前结束考勤
          </button>
        </div>
      </section>
    </div>
  );
}
