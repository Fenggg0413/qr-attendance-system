import { useEffect, useState } from 'react';
import { BarChart3, BookOpen, Building2, Check, Clock, PieChart, UsersRound, X } from 'lucide-react';
import { formatToday } from '../../../utils/format';
import { AbsenceWarnings } from './AbsenceWarnings';
import { CourseAttendanceTable } from './CourseAttendanceTable';
import { DonutSummary } from './DonutSummary';
import { TrendChart } from './TrendChart';

export function AdminDashboard({ client, session, onAuthExpired }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    client.get('/admin/dashboard')
      .then((next) => {
        if (!cancelled) setData(next);
      })
      .catch((err) => {
        if (err.status === 401) {
          onAuthExpired();
          return;
        }
        if (!cancelled) setError(err.message ?? '数据总览加载失败');
      });
    return () => {
      cancelled = true;
    };
  }, [client, onAuthExpired]);

  if (error) return <div className="error">{error}</div>;
  if (!data) return <div className="panel">加载中</div>;

  const kpis = [
    ['学生总数', data.kpis?.studentTotal ?? 0, UsersRound, 'blue'],
    ['今日出勤', data.kpis?.todayPresent ?? 0, Check, 'green'],
    ['今日缺勤', data.kpis?.todayAbsent ?? 0, X, 'red'],
    ['今日迟到', data.kpis?.todayLate ?? 0, Clock, 'orange'],
    ['课程总数', data.kpis?.courseTotal ?? 0, BookOpen, 'blue'],
    ['院系总数', data.kpis?.departmentTotal ?? 0, Building2, 'green'],
  ];

  return (
    <div className="adminDashboard">
      <section className="welcomeBanner">
        <div>
          <h1>管理员，您好</h1>
          <p>{formatToday()} —— 这是您的今日考勤概览。</p>
        </div>
      </section>

      <section className="adminKpiGrid">
        {kpis.map(([label, value, Icon, tone]) => (
          <article className="adminKpiCard" key={label}>
            <span className={`adminKpiIcon ${tone}`}><Icon size={20} /></span>
            <div>
              <p>{label}</p>
              <strong>{value}</strong>
            </div>
          </article>
        ))}
      </section>

      <section className="dashboardCharts">
        <div className="panel">
          <div className="panelHead"><h2><BarChart3 size={17} />近 7 天出勤趋势</h2></div>
          <TrendChart rows={data.trend ?? []} />
        </div>
        <div className="panel">
          <div className="panelHead"><h2><PieChart size={17} />总体出勤状态分布</h2></div>
          <DonutSummary distribution={data.distribution ?? {}} />
        </div>
      </section>

      <section className="dashboardLower">
        <CourseAttendanceTable rows={data.courseAttendance ?? []} />
        <AbsenceWarnings rows={data.absenceWarnings ?? []} />
      </section>
    </div>
  );
}
