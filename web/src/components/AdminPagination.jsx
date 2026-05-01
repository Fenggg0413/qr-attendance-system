export function AdminPagination({ total, page, totalPages, pageSize, onPage, onPageSize }) {
  return (
    <div className="adminPagination">
      <span>共 {total} 条</span>
      <label>
        每页条数
        <select value={pageSize} onChange={(event) => onPageSize(Number(event.target.value))}>
          {[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <button type="button" className="ghost" disabled={page <= 1} onClick={() => onPage(Math.max(1, page - 1))}>上一页</button>
      <strong>第 {page} / {totalPages} 页</strong>
      <button type="button" className="ghost" disabled={page >= totalPages} onClick={() => onPage(Math.min(totalPages, page + 1))}>下一页</button>
    </div>
  );
}
