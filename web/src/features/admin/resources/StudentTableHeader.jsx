import { Plus, Search } from 'lucide-react';

export function StudentTableHeader({ filters, onFilters, departments, grades, total, onApply, onReset, onCreate }) {
  return (
    <div className="studentTableHeader">
      <div className="studentSearchBar">
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="请输入姓名或学号"
              value={filters.query}
              onChange={(event) => onFilters({ ...filters, query: event.target.value })}
            />
          </span>
        </label>
        <label>
          院系
          <select value={filters.department} onChange={(event) => onFilters({ ...filters, department: event.target.value })}>
            <option>全部院系</option>
            {departments.map((department) => <option key={department.id}>{department.name}</option>)}
          </select>
        </label>
        <label>
          年级
          <select value={filters.grade} onChange={(event) => onFilters({ ...filters, grade: event.target.value })}>
            {grades.map((grade) => <option key={grade}>{grade}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="button" onClick={onApply}><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </div>
      <div className="studentTableActions">
        <strong>共搜索到 {total} 位学生</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />新增学生</button>
      </div>
    </div>
  );
}
