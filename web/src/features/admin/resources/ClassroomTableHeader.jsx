import { Plus, Search } from 'lucide-react';

export function ClassroomTableHeader({ filters, onFilters, buildings, total, onApply, onReset, onCreate }) {
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
              placeholder="请输入教室名称，如：101"
              value={filters.query}
              onChange={(event) => onFilters({ ...filters, query: event.target.value })}
            />
          </span>
        </label>
        <label>
          教学楼
          <select value={filters.building} onChange={(event) => onFilters({ ...filters, building: event.target.value })}>
            <option>全部教学楼</option>
            {buildings.map((building) => <option key={building}>{building}</option>)}
          </select>
        </label>
        <div className="studentSearchActions">
          <button type="submit"><Search size={16} />查询</button>
          <button type="button" className="ghost" onClick={onReset}>重置</button>
        </div>
      </form>
      <div className="studentTableActions">
        <strong>共 {total} 间教室</strong>
        <button type="button" onClick={onCreate}><Plus size={16} />新增教室</button>
      </div>
    </div>
  );
}
