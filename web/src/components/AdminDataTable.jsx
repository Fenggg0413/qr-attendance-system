import { KeyRound, PenLine, X } from 'lucide-react';
import { adminLabels } from '../constants/adminResources';
import { displayAdminRow, formatAdminCell } from '../utils/format';
import { SortableTableHeader } from './SortableTableHeader';

export function AdminDataTable({ rows, columns, labels = {}, loading, resourceTitle = '', onEdit, onDelete, onResetPassword, header = null, footer = null, sortColumn, sortDirection, onSort }) {
  const hasActions = Boolean(onEdit || onDelete || onResetPassword);
  return (
    <div className="panel adminTablePanel">
      {header}
      <div className="tableWrap">
        <table className="adminTable">
          <thead>
            <tr>
              {columns.map((column) => {
                const canSort = column !== 'display_index' && onSort;
                const label = labels[column] ?? adminLabels[column] ?? column;
                if (canSort) {
                  return (
                    <SortableTableHeader
                      key={column}
                      label={label}
                      column={column}
                      sortColumn={sortColumn}
                      sortDirection={sortDirection}
                      onSort={onSort}
                    />
                  );
                }
                return <th key={column}>{label}</th>;
              })}
              {hasActions && <th>操作</th>}
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={columns.length + (hasActions ? 1 : 0)}>加载中</td></tr>}
            {!loading && rows.map((row) => (
              <tr key={row.id ?? `${row.session_id}-${displayAdminRow(row)}`}>
                {columns.map((column) => <td key={column}>{formatAdminCell(row, column)}</td>)}
                {hasActions && (
                  <td>
                    <div className="actions">
                      {onEdit && <button type="button" className="ghost" aria-label={`编辑 ${resourceTitle} ${displayAdminRow(row)}`} onClick={() => onEdit(row)}><PenLine size={15} />编辑</button>}
                      {onResetPassword && <button type="button" className="ghost" aria-label={`重置密码 ${resourceTitle} ${displayAdminRow(row)}`} onClick={() => onResetPassword(row)}><KeyRound size={15} />重置密码</button>}
                      {onDelete && <button type="button" className="ghost" aria-label={`删除 ${resourceTitle} ${displayAdminRow(row)}`} onClick={() => onDelete(row.id)}><X size={15} />删除</button>}
                    </div>
                  </td>
                )}
              </tr>
            ))}
            {!loading && !rows.length && <tr><td colSpan={columns.length + (hasActions ? 1 : 0)}>暂无数据</td></tr>}
          </tbody>
        </table>
      </div>
      {footer}
    </div>
  );
}
