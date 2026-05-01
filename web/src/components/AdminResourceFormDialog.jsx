import { X } from 'lucide-react';

const gradeYearOptions = (() => {
  const current = new Date().getFullYear();
  return Array.from({ length: 8 }, (_, i) => current - i);
})();

const fieldPlaceholders = {
  name: '请输入姓名',
  username: '请输入登录账号',
  password: '留空则使用默认密码 (123456)',
  studentNo: '请输入学号',
  departmentId: '请选择所属院系',
};

const requiredFields = new Set(['name', 'username', 'studentNo', 'departmentId', 'grade']);

export function AdminResourceFormDialog({ title, fields, form, departments, submitLabel, onForm, onSubmit, onClose }) {
  const resourceName = title.includes('教师') ? '教师' : title.includes('教室') ? '教室' : '学生';
  // Determine if the last non-action field is odd-positioned (needs full-width)
  const lastFieldIdx = fields.length - 1;
  const lastFieldIsOdd = fields.length % 2 === 1;
  return (
    <div className="modalLayer">
      <section className="modal adminResourceDialog" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modalHead">
          <div>
            <h2>{title}</h2>
            <p>填写{resourceName}基础信息后保存。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label={`关闭${title}`}><X size={18} /></button>
        </div>
        <form className="modalBody adminResourceForm" onSubmit={onSubmit}>
          {fields.map(([name, label, meta = {}], idx) => {
            const isRequired = meta.required ?? requiredFields.has(name);
            const placeholder = meta.placeholder ?? fieldPlaceholders[name] ?? '';
            const isFullWidth = lastFieldIsOdd && idx === lastFieldIdx;
            return (
              <label key={name} className={isFullWidth ? 'fullWidthField' : ''}>
                <span className="requiredLabel">
                  {isRequired && <i>*</i>}{label}
                </span>
                {name === 'departmentId' ? (
                  <select
                    value={form[name] ?? ''}
                    required={isRequired}
                    onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                  >
                    <option value="">请选择所属院系</option>
                    {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                  </select>
                ) : (meta.type === 'year' || name === 'grade') ? (
                  <select
                    value={form[name] ?? ''}
                    required={isRequired}
                    onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                  >
                    <option value="">请选择入学年份</option>
                    {gradeYearOptions.map((year) => (
                      <option key={year} value={String(year)}>{year} 级</option>
                    ))}
                  </select>
                ) : (
                  <input
                    type={name === 'password' ? 'text' : 'text'}
                    placeholder={placeholder}
                    required={isRequired}
                    value={form[name] ?? ''}
                    onChange={(event) => onForm({ ...form, [name]: event.target.value })}
                  />
                )}
              </label>
            );
          })}
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit">确定</button>
          </div>
        </form>
      </section>
    </div>
  );
}
