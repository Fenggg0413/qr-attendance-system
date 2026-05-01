import { sortableAriaSort } from '../utils/format';

export function SortableTableHeader({ label, column, sortColumn, sortDirection, onSort }) {
  const isSorted = sortColumn === column;
  return (
    <th className="sortable" aria-sort={sortableAriaSort(column, sortColumn, sortDirection)}>
      <button type="button" className="sortButton" onClick={() => onSort(column)}>
        <span className="thContent">
          {label}
          <span className={`sortIcon ${isSorted ? 'active' : ''}`} aria-hidden="true">
            {isSorted ? (sortDirection === 'asc' ? '↑' : '↓') : '↕'}
          </span>
        </span>
      </button>
    </th>
  );
}
