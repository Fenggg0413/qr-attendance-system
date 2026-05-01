import { useState } from 'react';
import { Save, Search, UsersRound } from 'lucide-react';

export function StudentsPanel({ client, students, onStudentsChange }) {
  const [savingId, setSavingId] = useState(null);
  const [keyword, setKeyword] = useState('');
  const sorted = [...students].sort((a, b) => `${a.student_no}${a.name}`.localeCompare(`${b.student_no}${b.name}`, 'zh-Hans-CN'));
  const normalized = keyword.trim().toLowerCase();
  const filtered = normalized ? sorted.filter((s) => s.name.toLowerCase().includes(normalized) || s.student_no.toLowerCase().includes(normalized)) : sorted;

  async function save(student) {
    setSavingId(student.id);
    const saved = await client.put(`/teacher/students/${student.id}/note`, { note: student.note ?? '' });
    onStudentsChange(students.map((item) => (item.id === student.id ? { ...item, note: saved.note, saved: true } : item)));
    setSavingId(null);
  }

  return (
    <section className="panel">
      <div className="panelHead">
        <h2><UsersRound size={17} />学生名单管理</h2>
        <div className="panelHeadRight">
          <label className="searchField compact">
            <span>
              <Search size={15} />
              <input placeholder="搜索姓名或学号" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </span>
          </label>
          <span className="muted">{filtered.length} / {students.length} 人</span>
        </div>
      </div>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>学号</th>
              <th>姓名</th>
              <th>教师备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((student) => (
              <tr key={student.id}>
                <td>{student.student_no}</td>
                <td>{student.name}</td>
                <td>
                  <input
                    value={student.note ?? ''}
                    onChange={(event) => {
                      onStudentsChange(students.map((item) => (item.id === student.id ? { ...item, note: event.target.value, saved: false } : item)));
                    }}
                  />
                </td>
                <td>
                  <button className="ghost" disabled={savingId === student.id} onClick={() => save(student)}>
                    <Save size={15} />{student.saved ? '已保存' : '保存'}
                  </button>
                </td>
              </tr>
            ))}
            {!filtered.length && <tr><td colSpan="4" className="empty">没有匹配的学生</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}
