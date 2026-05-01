import { Search } from 'lucide-react';

export function AdminTableToolbar({ query, onQuery, departments = [], departmentFilter, onDepartmentFilter, grades = [], gradeFilter, onGradeFilter }) {
  return (
    <section className="adminTableToolbar panel">
      <label className="searchField">
        搜索
        <span>
          <Search size={16} />
          <input placeholder="搜索当前表格" value={query} onChange={(event) => onQuery(event.target.value)} />
        </span>
      </label>
      {departments.length > 0 && (
        <label>
          院系
          <select value={departmentFilter} onChange={(event) => onDepartmentFilter(event.target.value)}>
            <option>全部院系</option>
            {departments.map((department) => <option key={department.id}>{department.name}</option>)}
          </select>
        </label>
      )}
      {grades.length > 0 && (
        <label>
          年级
          <select value={gradeFilter} onChange={(event) => onGradeFilter(event.target.value)}>
            {grades.map((grade) => <option key={grade}>{grade}</option>)}
          </select>
        </label>
      )}
    </section>
  );
}
