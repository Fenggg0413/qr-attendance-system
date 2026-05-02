import { useState } from 'react';
import { Save, X } from 'lucide-react';
import { TeacherCombobox } from './TeacherCombobox';

export function ScheduleSlotDialog({ slot, teachers, classrooms, onSlot, onSubmit, onDelete, onClose }) {
  const [teacherQuery, setTeacherQuery] = useState('');
  return (
    <div className="modalLayer">
      <section className="modal scheduleSlotDialog" role="dialog" aria-modal="true" aria-label="编辑排课">
        <div className="modalHead">
          <div>
            <h2>编辑排课</h2>
            <p>{slot.weekday} · 第 {slot.period} 节</p>
          </div>
          <button type="button" className="iconButton" onClick={onClose} aria-label="关闭排课编辑"><X size={18} /></button>
        </div>
        <form className="modalBody" onSubmit={onSubmit}>
          {slot.error && <div className="error">{slot.error}</div>}
          <TeacherCombobox
            teachers={teachers}
            selectedId={slot.teacherId}
            query={teacherQuery}
            onQuery={setTeacherQuery}
            onSelect={(teacherId) => onSlot((current) => ({ ...current, teacherId }))}
          />
          <label>
            教室
            <select value={slot.classroomId} onChange={(event) => onSlot((current) => ({ ...current, classroomId: event.target.value }))}>
              <option value="">请选择教室</option>
              {classrooms.map((classroom) => <option key={classroom.id} value={classroom.id}>{classroom.name}</option>)}
            </select>
          </label>
          <fieldset className="segmentedField">
            <legend>课程类型</legend>
            <label>
              <input
                type="radio"
                name="courseType"
                checked={slot.courseType === 'LECTURE'}
                onChange={() => onSlot((current) => ({ ...current, courseType: 'LECTURE' }))}
              />
              <span>讲课</span>
            </label>
            <label>
              <input
                type="radio"
                name="courseType"
                checked={slot.courseType === 'LAB'}
                onChange={() => onSlot((current) => ({ ...current, courseType: 'LAB' }))}
              />
              <span>实验</span>
            </label>
          </fieldset>
          <div className="formActions dialogActions">
            {slot.id && <button type="button" className="ghost dangerButton" onClick={onDelete}>删除排课</button>}
            <button type="button" className="ghost" onClick={onClose}>取消</button>
            <button type="submit" disabled={!slot.teacherId || !slot.classroomId}><Save size={16} />保存排课</button>
          </div>
        </form>
      </section>
    </div>
  );
}
