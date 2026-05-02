import { useEffect, useMemo, useState } from 'react';
import { PenLine, Plus, Save, Search, X } from 'lucide-react';
import { AdminPageHead } from '../../../components/AdminPageHead';
import { AdminPagination } from '../../../components/AdminPagination';
import { SortableTableHeader } from '../../../components/SortableTableHeader';
import { useTableSort } from '../../../hooks/useTableSort';

export function AdminDepartmentsPage({ client }) {
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState({ name: '' });
  const [draftFilters, setDraftFilters] = useState({ query: '' });
  const [appliedFilters, setAppliedFilters] = useState({ query: '' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const filteredDepartments = useMemo(() => {
    const query = appliedFilters.query.trim().toLowerCase();
    if (!query) return departments;
    return departments.filter((department) =>
      String(department.name ?? '').toLowerCase().includes(query)
    );
  }, [departments, appliedFilters]);

  const { sortedRows: sortedDepartments, sortColumn: deptSortColumn, sortDirection: deptSortDirection, onSort: handleDeptSort } = useTableSort(filteredDepartments);

  const totalPages = Math.max(1, Math.ceil(sortedDepartments.length / pageSize));
  const pageDepartments = sortedDepartments.slice((page - 1) * pageSize, page * pageSize);
  const displayedRows = pageDepartments.map((row, index) => ({
    ...row,
    display_index: (page - 1) * pageSize + index + 1,
  }));

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setDepartments(await client.get('/admin/departments'));
    } catch (err) {
      setError(err.message ?? '院系数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    setEditId(null);
    setForm({ name: '' });
    setDialogOpen(true);
  }

  function openEdit(row) {
    setEditId(row.id);
    setForm({ name: row.name ?? '' });
    setDialogOpen(true);
  }

  function closeDialog() {
    setDialogOpen(false);
    setEditId(null);
    setForm({ name: '' });
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    try {
      if (editId) {
        await client.put(`/admin/departments/${editId}`, { name: form.name });
        setMessage('院系已更新');
      } else {
        await client.post('/admin/departments', { name: form.name });
        setMessage('院系已创建');
      }
      closeDialog();
      await load();
    } catch (err) {
      setError(err.message ?? '操作失败');
    }
  }

  async function remove(id) {
    if (!window.confirm('确认删除该院系？删除后不可恢复。')) return;
    setError('');
    setMessage('');
    try {
      await client.delete(`/admin/departments/${id}`);
      setMessage('院系已删除');
      await load();
    } catch (err) {
      setError(err.message ?? '删除失败');
    }
  }

  function applyFilters(event) {
    if (event) event.preventDefault();
    setAppliedFilters(draftFilters);
    setPage(1);
  }

  function resetFilters() {
    const empty = { query: '' };
    setDraftFilters(empty);
    setAppliedFilters(empty);
    setPage(1);
  }

  return (
    <div className="adminPage">
      <AdminPageHead title="院系管理" subtitle="维护院系基础数据，可新增、编辑或删除院系。" onRefresh={load} />
      {error && <div className="error">{error}</div>}
      {message && <div className="success">{message}</div>}
      <div className="panel adminTablePanel">
        <div className="studentTableHeader">
          <form className="studentSearchBar teacherSearchBar" onSubmit={applyFilters}>
            <label className="searchField">
              搜索
              <span>
                <Search size={16} />
                <input
                  placeholder="请输入院系名称"
                  value={draftFilters.query}
                  onChange={(event) => setDraftFilters({ ...draftFilters, query: event.target.value })}
                />
              </span>
            </label>
            <div className="studentSearchActions">
              <button type="submit"><Search size={16} />查询</button>
              <button type="button" className="ghost" onClick={resetFilters}>重置</button>
            </div>
          </form>
          <div className="studentTableActions">
            <strong>共 {filteredDepartments.length} 个院系</strong>
            <button type="button" onClick={openCreate}><Plus size={16} />新增院系</button>
          </div>
        </div>
        <div className="tableWrap">
          <table className="adminTable">
            <thead>
              <tr>
                <th>序号</th>
                <SortableTableHeader label="院系名称" column="name" sortColumn={deptSortColumn} sortDirection={deptSortDirection} onSort={handleDeptSort} />
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan="3">加载中</td></tr>}
              {!loading && displayedRows.map((row) => (
                <tr key={row.id}>
                  <td>{row.display_index}</td>
                  <td><strong>{row.name}</strong></td>
                  <td>
                    <div className="actions">
                      <button type="button" className="ghost" aria-label={`编辑 ${row.name}`} onClick={() => openEdit(row)}>
                        <PenLine size={15} />编辑
                      </button>
                      <button type="button" className="ghost" aria-label={`删除 ${row.name}`} onClick={() => remove(row.id)}>
                        <X size={15} />删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && !displayedRows.length && <tr><td colSpan="3">暂无院系数据</td></tr>}
            </tbody>
          </table>
        </div>
        <AdminPagination
          total={sortedDepartments.length}
          page={page}
          totalPages={totalPages}
          pageSize={pageSize}
          onPage={setPage}
          onPageSize={(nextSize) => {
            setPageSize(nextSize);
            setPage(1);
          }}
        />
      </div>
      {dialogOpen && (
        <div className="modalLayer">
          <section className="modal adminResourceDialog" role="dialog" aria-modal="true" aria-label={editId ? '编辑院系' : '新增院系'}>
            <div className="modalHead">
              <div>
                <h2>{editId ? '编辑院系' : '新增院系'}</h2>
                <p>{editId ? '修改院系名称后保存。' : '填写院系名称后创建。'}</p>
              </div>
              <button type="button" className="iconButton" onClick={closeDialog} aria-label="关闭"><X size={18} /></button>
            </div>
            <form className="modalBody adminResourceForm" onSubmit={submit}>
              <label className="fullWidthField">
                <span className="requiredLabel"><i>*</i>院系名称</span>
                <input
                  type="text"
                  placeholder="请输入院系名称，如：计算机科学与技术学院"
                  required
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                  autoFocus
                />
              </label>
              <div className="formActions dialogActions">
                <button type="button" className="ghost" onClick={closeDialog}>取消</button>
                <button type="submit"><Save size={16} />{editId ? '保存' : '确定'}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
