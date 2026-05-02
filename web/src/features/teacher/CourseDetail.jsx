import { Check, ChevronLeft, ClipboardCheck, Clock, Download, FileText, UsersRound } from 'lucide-react';
import { StatCard } from '../../components/StatCard';
import { courseNameText, csvCell, formatDate, methodText, statusText } from '../../utils/format';
import { sessionTotals } from '../../utils/filters';
import { SessionsTable } from './SessionsTable';
import { StudentsPanel } from './StudentsPanel';
import { VisualPanel } from './VisualPanel';

export function CourseDetail({ client, course, sessions, students, activeTab, loading, onBack, onTab, onOpenRecords, onStudentsChange, onDeleteSession }) {
  const totals = sessionTotals(sessions);
  const latest = sessions[0];

  function exportCsv() {
    const rows = [
      ['考勤编号', '开始时间', '方式', '状态', '已到', '缺勤', '请假', '迟到'],
      ...sessions.map((row) => [
        row.id,
        formatDate(row.started_at),
        methodText(row.method),
        statusText(row.status),
        row.present_count ?? 0,
        row.absent_count ?? 0,
        row.excused_count ?? 0,
        row.late_count ?? 0,
      ]),
    ];
    const csv = rows.map((row) => row.map(csvCell).join(',')).join('\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${course.name}-attendance.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="teacherPage">
      <section className="pageHead detailHead">
        <button className="iconButton" onClick={onBack} aria-label="返回课程列表"><ChevronLeft size={18} /></button>
        <div>
          <h1>{courseNameText(course.name)}</h1>
          <p>{course.code} · {course.class_name} · {course.semester}</p>
        </div>
      </section>

      <section className="statGrid">
        <StatCard icon={<UsersRound />} value={course.student_count ?? 0} label="学生数" />
        <StatCard icon={<ClipboardCheck />} value={sessions.length} label="考勤次数" />
        <StatCard icon={<Check />} value={totals.attendanceRate} label="到课率" />
        <StatCard icon={<Clock />} value={totals.absenceRate} label="缺勤率" />
      </section>

      <div className="tabs" role="tablist" aria-label="课程详情">
        <button role="tab" aria-selected={activeTab === 'records'} className={activeTab === 'records' ? 'active' : ''} onClick={() => onTab('records')}>考勤记录</button>
        <button role="tab" aria-selected={activeTab === 'students'} className={activeTab === 'students' ? 'active' : ''} onClick={() => onTab('students')}>学生名单</button>
        <button role="tab" aria-selected={activeTab === 'visual'} className={activeTab === 'visual' ? 'active' : ''} onClick={() => onTab('visual')}>可视化</button>
      </div>

      {activeTab === 'records' && (
        <section className="panel">
          <div className="panelHead">
            <h2><FileText size={17} />考勤记录</h2>
            <div className="actions">
              <button className="ghost" disabled={!sessions.length} onClick={exportCsv}><Download size={16} />导出数据</button>
            </div>
          </div>
          {loading ? <p className="hint">加载中</p> : <SessionsTable sessions={sessions} onOpenRecords={onOpenRecords} onDelete={onDeleteSession} />}
        </section>
      )}

      {activeTab === 'students' && (
        <StudentsPanel client={client} students={students} onStudentsChange={onStudentsChange} />
      )}

      {activeTab === 'visual' && (
        <VisualPanel sessions={sessions} latest={latest} students={students} />
      )}
    </div>
  );
}
