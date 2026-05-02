import { X } from 'lucide-react';

export function CourseFormDialog({ form, departments, onForm, onSubmit, onClose }) {
  return (
    <div className="modalLayer">
      <section className="modal adminResourceDialog" role="dialog" aria-modal="true" aria-label="创建课程">
        <div className="modalHead">
          <div>
            <h2>创建课程</h2>
            <p>创建基础课程后，可进入详情维护教师、排课和学生名单。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label="关闭创建课程"><X size={18} /></button>
        </div>
        <form className="modalBody adminResourceForm" onSubmit={onSubmit}>
          <label>
            <span className="requiredLabel"><i>*</i>课程名称</span>
            <input
              placeholder="请输入课程名称"
              required
              value={form.name}
              onChange={(event) => onForm({ ...form, name: event.target.value })}
            />
          </label>
          <label>
            <span className="requiredLabel"><i>*</i>课程代码</span>
            <input
              placeholder="请输入课程代码，如：CS101"
              required
              value={form.code}
              onChange={(event) => onForm({ ...form, code: event.target.value })}
            />
          </label>
          <label className="fullWidthField">
            <span className="requiredLabel"><i>*</i>所属院系</span>
            <select
              required
              value={form.departmentId}
              onChange={(event) => onForm({ ...form, departmentId: event.target.value })}
            >
              <option value="">请选择所属院系</option>
              {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
            </select>
          </label>
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit">确定</button>
          </div>
        </form>
      </section>
    </div>
  );
}
