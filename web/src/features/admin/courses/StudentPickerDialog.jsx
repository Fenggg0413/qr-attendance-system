import { Plus, Search, X } from 'lucide-react';

export function StudentPickerDialog({ students, search, selectedStudentIds, allSelected, someSelected, onSearch, onToggleStudent, onToggleAll, onClose, onConfirm }) {
  return (
    <div className="modalLayer">
      <section className="modal studentPickerDialog" role="dialog" aria-modal="true" aria-label="添加选课学生">
        <div className="modalHead">
          <div>
            <h2>添加选课学生</h2>
            <p>搜索并勾选需要加入课程的学生。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label="关闭添加学生"><X size={18} /></button>
        </div>
        <form className="modalBody" onSubmit={onConfirm}>
          <label className="searchField">
            搜索学生
            <span>
              <Search size={16} />
              <input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="搜索姓名、学号、账号或院系" />
            </span>
          </label>
          <div className="tableWrap studentPickerTable">
            <table>
              <thead><tr><th>
                        <input
                          type="checkbox"
                          aria-label="全选学生"
                          checked={allSelected}
                          ref={el => { if (el) el.indeterminate = someSelected && !allSelected; }}
                          onChange={onToggleAll}
                        />
                      </th><th>姓名</th><th>学号</th><th>院系</th></tr></thead>
              <tbody>
                {students.map((student) => {
                  const id = String(student.id);
                  return (
                    <tr key={student.id}>
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`${student.name} ${student.student_no}`}
                          checked={selectedStudentIds.includes(id)}
                          onChange={() => onToggleStudent(id)}
                        />
                      </td>
                      <td>{student.name}</td>
                      <td>{student.student_no}</td>
                      <td>{student.department_name}</td>
                    </tr>
                  );
                })}
                {!students.length && <tr><td colSpan="4">暂无可添加学生</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button disabled={!selectedStudentIds.length}><Plus size={16} />确认添加</button>
          </div>
        </form>
      </section>
    </div>
  );
}
