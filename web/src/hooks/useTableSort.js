import { useCallback, useMemo, useState } from 'react';
import { handleColumnSort, sortRows } from '../utils/filters';

export function useTableSort(rows) {
  const [sort, setSort] = useState({ sortColumn: null, sortDirection: null });
  const sortedRows = useMemo(() => sortRows(rows, sort.sortColumn, sort.sortDirection), [rows, sort.sortColumn, sort.sortDirection]);
  const onSort = useCallback((column) => {
    setSort((current) => handleColumnSort(column, current.sortColumn, current.sortDirection));
  }, []);
  const resetSort = useCallback(() => {
    setSort({ sortColumn: null, sortDirection: null });
  }, []);
  return {
    sortedRows,
    sortColumn: sort.sortColumn,
    sortDirection: sort.sortDirection,
    onSort,
    resetSort,
  };
}
