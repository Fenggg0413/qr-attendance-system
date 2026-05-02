import { useMemo, useState } from 'react';
import { teacherOptionText } from '../../../utils/format';

export function TeacherCombobox({ teachers, selectedId, query, onQuery, onSelect }) {
  const [open, setOpen] = useState(false);
  const selectedTeacher = teachers.find((teacher) => String(teacher.id) === String(selectedId));
  const selectedLabel = selectedTeacher ? teacherOptionText(selectedTeacher) : '';
  const filteredTeachers = useMemo(() => {
    const term = query.trim().toLowerCase();
    return teachers.filter((teacher) => {
      if (!term) return true;
      return [teacher.name, teacher.username, teacher.department_name, teacher.department, String(teacher.id)]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
    });
  }, [query, teachers]);

  return (
    <label className="comboField">
      授课教师
      <div className="comboBox">
        <input
          role="combobox"
          aria-expanded={open}
          aria-controls="teacher-options"
          value={open ? query : selectedLabel}
          placeholder="输入教师姓名、账号或院系"
          onFocus={() => {
            setOpen(true);
            onQuery('');
          }}
          onChange={(event) => {
            setOpen(true);
            onQuery(event.target.value);
          }}
        />
        {open && (
          <div className="comboMenu" id="teacher-options" role="listbox">
            {filteredTeachers.map((teacher) => (
              <button
                type="button"
                className="comboOption"
                key={teacher.id}
                role="option"
                aria-selected={String(teacher.id) === String(selectedId)}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  onSelect(String(teacher.id));
                  onQuery('');
                  setOpen(false);
                }}
              >
                {teacherOptionText(teacher)}
              </button>
            ))}
            {!filteredTeachers.length && <span className="comboEmpty">暂无匹配教师</span>}
          </div>
        )}
      </div>
    </label>
  );
}
