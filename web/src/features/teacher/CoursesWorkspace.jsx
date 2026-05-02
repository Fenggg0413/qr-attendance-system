import { useState } from 'react';
import { BookOpen, Search } from 'lucide-react';
import { courseNameText } from '../../utils/format';

export function CoursesWorkspace({ courses, error, onOpenDetail }) {
  const [keyword, setKeyword] = useState('');
  const [semester, setSemester] = useState('全部学期');
  const semesters = ['全部学期', ...Array.from(new Set(courses.map((course) => course.semester).filter(Boolean)))];
  const normalized = keyword.trim().toLowerCase();
  const filtered = courses.filter((course) => {
    const inSemester = semester === '全部学期' || course.semester === semester;
    const text = `${course.name} ${course.code} ${course.class_name}`.toLowerCase();
    return inSemester && (!normalized || text.includes(normalized));
  });

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>我的课程</h1>
        </div>
        <div className="summaryStrip">
          <span><BookOpen size={16} />课程 {courses.length}</span>
        </div>
      </section>

      <section className="toolbar panel">
        <label>
          学期
          <select value={semester} onChange={(event) => setSemester(event.target.value)}>
            {semesters.map((item) => <option key={item}>{item}</option>)}
          </select>
        </label>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="搜索课程名称、代码或班级"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </span>
        </label>
      </section>

      {error && <div className="error">{error}</div>}
      <section className="courseCards">
        {filtered.map((course) => (
          <article className="courseCard" key={course.id}>
            <div>
              <span className="eyebrow">{course.semester}</span>
              <h2>{courseNameText(course.name)}</h2>
              <p>{course.code} · {course.class_name}</p>
            </div>
            <div className="cardActions">
              <button onClick={() => onOpenDetail(course)}>查看详情</button>
            </div>
          </article>
        ))}
        {!filtered.length && <div className="empty panel">没有匹配的课程</div>}
      </section>
    </div>
  );
}
