import { Save, X } from 'lucide-react';

export function ClassroomFormDialog({ title, form, buildings, isEdit, onForm, onSubmit, onClose }) {
  return (
    <div className="modalLayer">
      <section className="modal classroomDialog" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modalHead">
          <div>
            <h2>{title}</h2>
            <p>填写教室基础信息后保存。</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label={`关闭${title}`}><X size={18} /></button>
        </div>
        <form className="modalBody classroomForm" onSubmit={onSubmit}>
          <label>
            <span className="requiredLabel"><i>*</i>教室名称</span>
            <input
              type="text"
              placeholder="请输入，如：101"
              value={form.name ?? ''}
              onChange={(event) => onForm({ ...form, name: event.target.value })}
              required
            />
          </label>
          <label>
            <span className="requiredLabel"><i>*</i>教学楼</span>
            <select
              value={form.building ?? ''}
              onChange={(event) => onForm({ ...form, building: event.target.value })}
              required
            >
              <option value="">请选择所属教学楼</option>
              {buildings.map((building) => <option key={building} value={building}>{building}</option>)}
            </select>
          </label>
          <label>
            <span className="requiredLabel"><i>*</i>容量</span>
            <input
              type="number"
              min="1"
              step="1"
              placeholder="请输入容纳人数"
              value={form.capacity ?? ''}
              onChange={(event) => onForm({ ...form, capacity: event.target.value })}
              required
            />
          </label>
          <div className="formActions dialogActions">
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit"><Save size={16} />{isEdit ? '保存' : '确定'}</button>
          </div>
        </form>
      </section>
    </div>
  );
}
