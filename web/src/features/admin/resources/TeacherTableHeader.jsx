import { Plus, Search } from 'lucide-react';

export function TeacherTableHeader({ filters, onFilters, departments, total, onApply, onReset, onCreate }) {
  function submitFilters(event) {
    event.preventDefault();
    onApply();
  }

  return (
    <div className="studentTableHeader">
      <form className="studentSearchBar teacherSearchBar" onSubmit={submitFilters}>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="请输入教师姓名或账号"
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
        <div className="studentSearchActions">
          <button type="submit"><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </form>
      <div className="studentTableActions">
        <strong>共 {total} 位教师</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />新增教师</button>
      </div>
    </div>
  );
}
