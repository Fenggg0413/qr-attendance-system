import { Plus, Search } from 'lucide-react';

export function CourseTableHeader({ filters, departments, terms, total, onFilters, onApply, onReset, onCreate }) {
  return (
    <div className="studentTableHeader courseTableHeader">
      <form className="studentSearchBar courseSearchBar" onSubmit={onApply}>
        <label className="searchField">
          搜索
          <span>
            <Search size={16} />
            <input
              placeholder="搜索课程名称或代码"
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
          学期
          <select value={filters.term} onChange={(event) => onFilters({ ...filters, term: event.target.value })}>
            {terms.map((term) => <option key={term}>{term}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="submit"><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </form>
      <div className="studentTableActions">
        <strong>共搜索到 {total} 门课程</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />创建课程</button>
      </div>
    </div>
  );
}
