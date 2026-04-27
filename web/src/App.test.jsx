import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import App from './main.jsx';

const courses = [
  { id: 1, name: 'Java Web 开发', code: 'JAVA-WEB-01', class_name: '软件 2301', student_count: 2, semester: '2025-2026 第二学期' },
  { id: 2, name: 'Data Structure', code: 'DATA-01', class_name: '软件 2302', student_count: 1, semester: '2025-2026 第二学期' },
];

const sessions = [
  {
    id: 9,
    course_id: 1,
    started_at: '2026-04-25T08:00:00Z',
    ends_at: '2026-04-25T08:05:00Z',
    status: 'CLOSED',
    method: 'QR',
    present_count: 1,
    absent_count: 1,
    excused_count: 0,
    late_count: 0,
  },
];

const records = [
  { id: 1, student_id: 1, student_name: '李同学', student_no: '20230001', status: 'PRESENT', checked_in_at: '2026-04-25T08:01:00Z', source: 'QR' },
  { id: null, student_id: 2, student_name: '王同学', student_no: '20230002', status: 'ABSENT', checked_in_at: null, source: null },
];

beforeEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

afterEach(() => {
  cleanup();
});

test('login protects the portal until credentials are submitted', async () => {
  mockTeacherApi();

  render(<App />);

  expect(screen.getByRole('heading', { name: '动态二维码考勤' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  await waitFor(() => expect(screen.getByRole('heading', { name: /教师，您好/ })).toBeInTheDocument());
  expect(screen.getByText(/张老师/)).toBeInTheDocument();
  expect(global.fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' }));
});

test('expired admin session returns to login instead of showing unauthorized dashboard', async () => {
  localStorage.setItem('qr-attendance-session', JSON.stringify({
    token: 'stale-admin-token',
    user: { id: 1, username: 'admin', role: 'ADMIN', displayName: '系统管理员' },
  }));
  global.fetch = vi.fn(async (url) => {
    if (url.endsWith('/admin/dashboard')) {
      return errorResponse(401, { error: 'Unauthorized' });
    }
    return response({});
  });

  render(<App />);

  await waitFor(() => expect(screen.getByRole('heading', { name: '动态二维码考勤' })).toBeInTheDocument());
  expect(localStorage.getItem('qr-attendance-session')).toBeNull();
});

test('teacher can filter courses and open detail tabs', async () => {
  mockTeacherApi();

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /教师，您好/ })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '我的课程' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Java Web 开发' })).toBeInTheDocument());
  const summaryStrip = container.querySelector('.summaryStrip');
  expect(within(summaryStrip).getByText('课程 2')).toBeInTheDocument();
  expect(within(summaryStrip).queryByText(/学生/)).not.toBeInTheDocument();
  expect(container.querySelector('.courseMeta')).not.toBeInTheDocument();
  const firstCourseCard = container.querySelector('.courseCard');
  expect(within(firstCourseCard).getAllByRole('button').map((button) => button.textContent)).toEqual(['查看详情', '发起考勤']);

  await userEvent.type(screen.getByPlaceholderText('搜索课程名称、代码或班级'), 'Data');
  expect(screen.getByText('Data Structure')).toBeInTheDocument();
  expect(screen.queryByText('Java Web 开发')).not.toBeInTheDocument();

  await userEvent.clear(screen.getByPlaceholderText('搜索课程名称、代码或班级'));
  await userEvent.click(screen.getAllByRole('button', { name: '查看详情' })[0]);

  await waitFor(() => expect(screen.getByRole('heading', { name: 'Java Web 开发' })).toBeInTheDocument());
  expect(screen.getByRole('tab', { name: '考勤记录' })).toHaveAttribute('aria-selected', 'true');
  await userEvent.click(screen.getByRole('tab', { name: '学生名单' }));
  await waitFor(() => expect(screen.getByDisplayValue('需要关注')).toBeInTheDocument());
  await userEvent.click(screen.getByRole('tab', { name: '可视化' }));
  expect(screen.getByText('高风险缺勤学生排行')).toBeInTheDocument();
});

test('course search input keeps focus cursor without browser outline', () => {
  const css = readFileSync(resolve(__dirname, 'styles.css'), 'utf8');

  expect(css).toMatch(/\.searchField input:focus\s*\{[^}]*outline:\s*none;?[^}]*\}/);
});

test('admin course detail action is centered on the right side of the card', () => {
  const css = readFileSync(resolve(__dirname, 'styles.css'), 'utf8');

  expect(css).toMatch(/\.adminCourseCard\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+auto;?[^}]*align-items:\s*center;?[^}]*\}/);
  expect(css).toMatch(/\.adminCourseCard\s+\.ghost\s*\{[^}]*align-self:\s*center;?[^}]*justify-self:\s*end;?[^}]*\}/);
});

test('teacher can start attendance and view record drawer', async () => {
  mockTeacherApi();
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /教师，您好/ })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '我的课程' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Java Web 开发' })).toBeInTheDocument());

  await userEvent.click(screen.getAllByRole('button', { name: '发起考勤' })[0]);
  expect(screen.getByRole('dialog', { name: '发起考勤' })).toBeInTheDocument();
  expect(screen.queryByLabelText('数字验证码')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('手动点名')).not.toBeInTheDocument();
  expect(screen.getByText('二维码考勤')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '立即开始' }));

  await waitFor(() => expect(screen.getByText('动态签到码')).toBeInTheDocument());
  expect(JSON.parse(global.fetch.mock.calls.find(([url, options]) => url.endsWith('/teacher/courses/1/attendance-sessions') && options?.method === 'POST')[1].body).method).toBe('QR');
  await waitFor(() => expect(screen.getByText('李同学')).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '提前结束考勤' }));
  await waitFor(() => expect(screen.getByText('考勤已结束')).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '隐藏窗口，考勤继续' }));
  await userEvent.click(screen.getAllByRole('button', { name: '查看详情' })[0]);
  await waitFor(() => expect(screen.getByRole('button', { name: '查看明细' })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '查看明细' }));
  const drawer = screen.getByRole('dialog', { name: /考勤明细/ });
  expect(within(drawer).getByText('王同学')).toBeInTheDocument();
});

test('profile form validates password confirmation and logout returns to login', async () => {
  mockTeacherApi();

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '个人中心' })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '个人中心' }));
  await waitFor(() => expect(screen.getByDisplayValue('teacher@example.com')).toBeInTheDocument());
  await userEvent.clear(screen.getByLabelText('新密码'));
  await userEvent.type(screen.getByLabelText('新密码'), 'newpass123');
  await userEvent.type(screen.getByLabelText('确认新密码'), 'mismatch');
  await userEvent.click(screen.getByRole('button', { name: '修改密码' }));
  expect(screen.getByText('两次输入的新密码不一致')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '退出登录' }));
  expect(screen.getByRole('heading', { name: '动态二维码考勤' })).toBeInTheDocument();
});

test('admin dashboard and simplified navigation render real overview data', async () => {
  mockAdminApi();

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());
  expect(screen.getByRole('button', { name: '仪表盘' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '学生管理' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '教师管理' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '教室管理' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '课程管理' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '选课名单' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '考勤统计' })).not.toBeInTheDocument();
  expect(screen.getByText('学生总数')).toBeInTheDocument();
  expect(screen.getByText('近 7 天出勤趋势')).toBeInTheDocument();
  expect(screen.getByText('总体出勤状态分布')).toBeInTheDocument();
  expect(screen.getAllByText('生成式 AI').length).toBeGreaterThan(0);
  expect(screen.getByText('最近活动记录')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '标记考勤' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '添加学生' })).not.toBeInTheDocument();
});

test('admin can manage classrooms for schedule slot selection', async () => {
  mockAdminApi();

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '教室管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '教室管理' })).toBeInTheDocument());
  expect(screen.getByRole('columnheader', { name: '教室名称' })).toBeInTheDocument();
  expect(screen.getByText('教一-301')).toBeInTheDocument();
  expect(screen.queryByLabelText('院系')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '新增教室' }));
  const classroomDialog = screen.getByRole('dialog', { name: '新增教室' });
  await userEvent.type(within(classroomDialog).getByLabelText('*教室名称'), '综合楼202');
  await userEvent.selectOptions(within(classroomDialog).getByLabelText('*教学楼'), '教一');
  await userEvent.type(within(classroomDialog).getByLabelText('*容量'), '64');
  await userEvent.click(within(classroomDialog).getByRole('button', { name: '确定' }));

  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/classrooms', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ name: '综合楼202', building: '教一', capacity: '64' }),
  })));
  await waitFor(() => expect(screen.getByText('综合楼202')).toBeInTheDocument());
});

test('admin can filter students and open course detail management', async () => {
  mockAdminApi();

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '学生管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '学生管理' })).toBeInTheDocument());
  expect(screen.queryByLabelText('初始密码')).not.toBeInTheDocument();
  expect(screen.getByText('所属院系')).toBeInTheDocument();
  expect(screen.getByText('共搜索到 3 位学生')).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText('院系'), '人工智能学院');
  expect(screen.getByText('测试学生')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '查询' }));
  expect(screen.getByText('课程学生')).toBeInTheDocument();
  expect(screen.queryByText('测试学生')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '重置' }));
  expect(screen.getByText('测试学生')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '新增学生' }));
  const studentDialog = screen.getByRole('dialog', { name: '新增学生' });
  expect(within(studentDialog).getByLabelText('初始密码')).toHaveAttribute('type', 'text');
  expect(within(studentDialog).getByLabelText('初始密码')).toHaveValue('');
  await userEvent.type(within(studentDialog).getByLabelText('*姓名'), '新增学生');
  await userEvent.type(within(studentDialog).getByLabelText('*账号'), 'student4');
  await userEvent.type(within(studentDialog).getByLabelText('*学号'), '20230004');
  await userEvent.selectOptions(within(studentDialog).getByLabelText('*年级'), '2026 级');
  await userEvent.selectOptions(within(studentDialog).getByLabelText('*所属院系'), '计算机学院');
  await userEvent.click(within(studentDialog).getByRole('button', { name: '确定' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/students', expect.objectContaining({
    method: 'POST',
    body: expect.stringContaining('"grade":"2026"'),
  })));
  await waitFor(() => expect(screen.getAllByText('新增学生').length).toBeGreaterThan(1));
  expect(screen.getAllByText('2026').length).toBeGreaterThan(1);

  await userEvent.click(screen.getByRole('button', { name: '编辑 学生 测试学生' }));
  const editStudentDialog = screen.getByRole('dialog', { name: '编辑学生' });
  expect(within(editStudentDialog).getByLabelText('*年级')).toHaveValue('2023');
  await userEvent.selectOptions(within(editStudentDialog).getByLabelText('*年级'), '2022 级');
  await userEvent.click(within(editStudentDialog).getByRole('button', { name: '确定' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/students/1', expect.objectContaining({
    method: 'PUT',
    body: expect.stringContaining('"grade":"2022"'),
  })));

  await userEvent.click(screen.getByRole('button', { name: '课程管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '课程管理' })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '查看课程 生成式 AI' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '生成式 AI' })).toBeInTheDocument());
  expect(screen.getByText('授课安排').compareDocumentPosition(screen.getByText('排课管理')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(screen.getByText('排课管理')).toBeInTheDocument();
  expect(screen.queryByText('固定星期和时间格式，减少录入错误。')).not.toBeInTheDocument();
  expect(screen.queryByText('搜索教师姓名、账号或院系后选择授课人。')).not.toBeInTheDocument();
  expect(screen.getByLabelText('排课网格')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '编辑排课 周二 第3节 李老师 教一-301 讲课' })).toBeInTheDocument();
  expect(screen.queryByLabelText('开始时间')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('结束时间')).not.toBeInTheDocument();
  expect(screen.getByLabelText('学期').tagName).toBe('SELECT');
  expect(screen.queryByLabelText('教师 ID')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('搜索教师')).not.toBeInTheDocument();
  expect(screen.getByLabelText('授课教师')).toHaveValue('李老师（teacher2） · 人工智能学院');
  expect(screen.queryByLabelText('学生 ID')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('搜索学生')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '添加学生' })).toBeInTheDocument();
  expect(screen.getByText('教一-301')).toBeInTheDocument();
  expect(screen.getByText('课程学生')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '添加学生' }));
  const dialog = screen.getByRole('dialog', { name: '添加选课学生' });
  await userEvent.type(within(dialog).getByLabelText('搜索学生'), '学生');
  expect(within(dialog).queryByText('课程学生')).not.toBeInTheDocument();
  await userEvent.click(within(dialog).getByRole('checkbox', { name: /测试学生/ }));
  await userEvent.click(within(dialog).getByRole('checkbox', { name: /候选学生/ }));
  await userEvent.click(within(dialog).getByRole('button', { name: '确认添加' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/courses/2/students', expect.objectContaining({ method: 'POST', body: JSON.stringify({ studentId: 1 }) })));
  expect(global.fetch).toHaveBeenCalledWith('/api/admin/courses/2/students', expect.objectContaining({ method: 'POST', body: JSON.stringify({ studentId: 3 }) }));

  await userEvent.click(screen.getByRole('button', { name: '移除 课程学生' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/courses/2/students/2', expect.objectContaining({ method: 'DELETE' })));
});

test('admin edits schedule slots in an overlay while keeping the global top bar style', async () => {
  mockAdminApi();

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '课程管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '课程管理' })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '查看课程 生成式 AI' }));

  await waitFor(() => expect(screen.getByRole('heading', { name: '生成式 AI' })).toBeInTheDocument());
  expect(container.querySelector('.topBar')).toBeInTheDocument();
  expect(container.querySelector('.scheduleGrid')).toBeInTheDocument();
  expect(screen.queryByLabelText('开始时间')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '排课 周一 第1节' }));
  const createDialog = screen.getByRole('dialog', { name: '编辑排课' });
  await userEvent.click(within(createDialog).getByLabelText('授课教师'));
  await userEvent.click(within(createDialog).getByRole('option', { name: '李老师（teacher2） · 人工智能学院' }));
  await userEvent.selectOptions(within(createDialog).getByLabelText('教室'), '2');
  await userEvent.click(within(createDialog).getByRole('radio', { name: '实验' }));
  await userEvent.click(within(createDialog).getByRole('button', { name: '保存排课' }));

  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/courses/2/schedule-slots', expect.objectContaining({
    method: 'PUT',
    body: JSON.stringify({ weekday: '周一', period: 1, teacherId: 2, classroomId: 2, courseType: 'LAB' }),
  })));
  await waitFor(() => expect(screen.getByText('实验楼101')).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '排课 周二 第4节' }));
  const conflictDialog = screen.getByRole('dialog', { name: '编辑排课' });
  await userEvent.click(within(conflictDialog).getByLabelText('授课教师'));
  await userEvent.click(within(conflictDialog).getByRole('option', { name: '李老师（teacher2） · 人工智能学院' }));
  await userEvent.selectOptions(within(conflictDialog).getByLabelText('教室'), '1');
  await userEvent.click(within(conflictDialog).getByRole('button', { name: '保存排课' }));
  await waitFor(() => expect(within(conflictDialog).getByText('该教室此节次已被占用')).toBeInTheDocument());

  await userEvent.click(within(conflictDialog).getByRole('button', { name: '取消' }));
  await userEvent.click(screen.getByRole('button', { name: '编辑排课 周二 第3节 李老师 教一-301 讲课' }));
  const editDialog = screen.getByRole('dialog', { name: '编辑排课' });
  expect(within(editDialog).getByLabelText('教室')).toHaveValue('1');
  await userEvent.click(within(editDialog).getByRole('button', { name: '删除排课' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/courses/2/schedule-slots/7', expect.objectContaining({ method: 'DELETE' })));
});

test('admin student table shows continuous row numbers instead of database ids', async () => {
  mockAdminApi({ gappedStudentIds: true });

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '学生管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '学生管理' })).toBeInTheDocument());

  expect(screen.getByRole('columnheader', { name: '序号' })).toBeInTheDocument();
  expect(screen.queryByRole('columnheader', { name: 'ID' })).not.toBeInTheDocument();
  const firstColumnValues = Array.from(container.querySelectorAll('.adminTable tbody tr'))
    .map((row) => row.querySelector('td')?.textContent);
  expect(firstColumnValues).toEqual(['1', '2']);

  await userEvent.click(screen.getByRole('button', { name: '删除 学生 黄同学' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/students/4', expect.objectContaining({ method: 'DELETE' })));
});

test('admin course management uses searchable table and modal creation', async () => {
  mockAdminApi();

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());

  await userEvent.click(screen.getByRole('button', { name: '课程管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '课程管理' })).toBeInTheDocument());

  expect(screen.queryByText('2 条')).not.toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: '序号' })).toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: '授课教师' })).toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: '学期' })).toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: '选课人数' })).toBeInTheDocument();
  expect(Array.from(container.querySelectorAll('.courseDataTable tbody tr')).map((row) => row.querySelector('td')?.textContent)).toEqual(['1', '2']);
  expect(screen.getByText('李老师')).toBeInTheDocument();
  expect(screen.getAllByText('2026 春季').length).toBeGreaterThan(0);
  expect(screen.getByText('1 人')).toBeInTheDocument();

  await userEvent.type(screen.getByPlaceholderText('搜索课程名称或代码'), 'GEN');
  await userEvent.click(screen.getByRole('button', { name: '查询' }));
  expect(screen.getByText('生成式 AI')).toBeInTheDocument();
  expect(screen.queryByText('数据结构')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '重置' }));
  expect(screen.getByText('数据结构')).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText('院系'), '人工智能学院');
  await userEvent.click(screen.getByRole('button', { name: '查询' }));
  expect(screen.queryByText('数据结构')).not.toBeInTheDocument();
  expect(screen.getByText('生成式 AI')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '创建课程' }));
  const dialog = screen.getByRole('dialog', { name: '创建课程' });
  await userEvent.type(within(dialog).getByLabelText('*课程名称'), '机器学习');
  await userEvent.type(within(dialog).getByLabelText('*课程代码'), 'ML-01');
  await userEvent.selectOptions(within(dialog).getByLabelText('*所属院系'), '计算机学院');
  await userEvent.click(within(dialog).getByRole('button', { name: '确定' }));

  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/courses', expect.objectContaining({
    method: 'POST',
    body: expect.stringContaining('"code":"ML-01"'),
  })));
});

test('admin student table paginates client-side results', async () => {
  mockAdminApi({ manyStudents: true });

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '学生管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '学生管理' })).toBeInTheDocument());

  expect(screen.getByText('共搜索到 13 位学生')).toBeInTheDocument();
  expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument();
  expect(screen.getByText('批量学生10')).toBeInTheDocument();
  expect(screen.queryByText('批量学生11')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '下一页' }));
  expect(screen.getByText('第 2 / 2 页')).toBeInTheDocument();
  expect(screen.getByText('批量学生11')).toBeInTheDocument();

  await userEvent.selectOptions(screen.getByLabelText('每页条数'), '20');
  expect(screen.getByText('第 1 / 1 页')).toBeInTheDocument();
  expect(screen.getByText('批量学生13')).toBeInTheDocument();
});

test('admin course detail hides option-loading 404 messages', async () => {
  mockAdminApi({ termsNotFound: true });

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '课程管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '课程管理' })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '查看课程 生成式 AI' }));

  await waitFor(() => expect(screen.getByRole('heading', { name: '生成式 AI' })).toBeInTheDocument());
  await waitFor(() => expect(screen.getByLabelText('授课教师')).toHaveValue('李老师（teacher2） · 人工智能学院'));
  expect(screen.queryByText('Not Found')).not.toBeInTheDocument();
  expect(screen.getByLabelText('学期')).toHaveValue('2026 春季');
});

test('admin teacher management still supports editing basic data', async () => {
  mockAdminApi();
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: /管理员，您好/ })).toBeInTheDocument());
  await userEvent.click(screen.getByRole('button', { name: '教师管理' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '教师管理' })).toBeInTheDocument());
  expect(screen.queryByLabelText('初始密码')).not.toBeInTheDocument();
  expect(screen.queryByRole('columnheader', { name: 'ID' })).not.toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: '序号' })).toBeInTheDocument();
  expect(Array.from(container.querySelectorAll('.adminTable tbody tr')).map((row) => row.querySelector('td')?.textContent)).toEqual(['1', '2']);
  expect(screen.getByText('共 2 位教师')).toBeInTheDocument();
  expect(screen.getByText('第 1 / 1 页')).toBeInTheDocument();

  await userEvent.type(screen.getByPlaceholderText('请输入教师姓名或账号'), 'teacher2');
  expect(screen.getByText('张老师')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '查询' }));
  expect(screen.getByText('李老师')).toBeInTheDocument();
  expect(screen.queryByText('张老师')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '重置' }));
  expect(screen.getByText('张老师')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '新增教师' }));
  const createDialog = screen.getByRole('dialog', { name: '新增教师' });
  expect(within(createDialog).getByLabelText('初始密码')).toHaveAttribute('type', 'text');
  await userEvent.click(within(createDialog).getByRole('button', { name: '取消' }));

  await userEvent.click(screen.getByRole('button', { name: '编辑 教师 张老师' }));
  const editDialog = screen.getByRole('dialog', { name: '编辑教师' });
  expect(within(editDialog).queryByLabelText('初始密码')).not.toBeInTheDocument();
  await userEvent.clear(within(editDialog).getByLabelText('*姓名'));
  await userEvent.type(within(editDialog).getByLabelText('*姓名'), '张老师更新');
  await userEvent.click(within(editDialog).getByRole('button', { name: '确定' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/teachers/1', expect.objectContaining({ method: 'PUT' })));

  await userEvent.click(screen.getByRole('button', { name: '重置密码 教师 张老师更新' }));
  expect(confirm).toHaveBeenCalledWith('确认将「张老师更新」的密码重置为 123456？');
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/teachers/1/reset-password', expect.objectContaining({ method: 'POST' })));
  expect(screen.getByText('张老师更新的密码已重置为 123456')).toBeInTheDocument();
});

function mockTeacherApi() {
  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method ?? 'GET';
    if (url.endsWith('/auth/login')) {
      return response({ token: 'jwt', user: { id: 10, username: 'teacher1', role: 'TEACHER', displayName: '张老师' } });
    }
    if (url.endsWith('/teacher/dashboard')) {
      return response({
        kpis: { courseTotal: 2, studentTotal: 3, todayPresent: 1, todayAbsent: 1, todayLate: 0, sessionTotal: 1 },
        trend: [
          { date: '2026-04-20', present: 1, absent: 0, late: 0 },
          { date: '2026-04-21', present: 2, absent: 1, late: 0 },
          { date: '2026-04-22', present: 1, absent: 0, late: 0 },
          { date: '2026-04-23', present: 1, absent: 0, late: 0 },
          { date: '2026-04-24', present: 2, absent: 0, late: 0 },
          { date: '2026-04-25', present: 1, absent: 1, late: 0 },
          { date: '2026-04-26', present: 1, absent: 1, late: 0 },
        ],
        distribution: { present: 9, absent: 3, late: 0, rate: 75 },
        courseAttendance: [
          { course_id: 1, course_name: 'Java Web 开发', total: 2, present: 5, absent: 2, late: 0 },
          { course_id: 2, course_name: 'Data Structure', total: 1, present: 4, absent: 1, late: 0 },
        ],
        recentActivities: [
          { id: 1, session_id: 9, student_name: '李同学', course_name: 'Java Web 开发', student_no: '20230001', checked_in_at: '2026-04-25T08:01:00Z', status: 'PRESENT' },
        ],
      });
    }
    if (url.endsWith('/teacher/courses') && method === 'GET') return response(courses);
    if (url.endsWith('/teacher/courses/1') && method === 'GET') return response(courses[0]);
    if (url.endsWith('/teacher/courses/1/attendance-sessions') && method === 'GET') return response(sessions);
    if (url.endsWith('/teacher/courses/1/attendance-sessions') && method === 'POST') {
      return response({ id: 10, courseId: 1, method: 'QR', startedAt: '2099-04-25T08:10:00Z', endsAt: '2099-04-25T08:15:00Z', status: 'OPEN' });
    }
    if (url.endsWith('/teacher/courses/1/students')) {
      return response([
        { id: 1, name: '李同学', student_no: '20230001', note: '需要关注' },
        { id: 2, name: '王同学', student_no: '20230002', note: '' },
      ]);
    }
    if (url.endsWith('/teacher/attendance-sessions/9/records') || url.endsWith('/teacher/attendance-sessions/10/records')) return response(records);
    if (url.endsWith('/teacher/attendance-sessions/10/qr')) return response({ sessionId: 10, payload: 'qr-attendance://checkin?sessionId=10&token=abc', token: 'abc', expiresAt: '2026-04-25T08:11:00Z' });
    if (url.endsWith('/teacher/attendance-sessions/10/close')) return response({ id: 10, status: 'CLOSED', method: 'CODE' });
    if (url.endsWith('/teacher/profile') && method === 'GET') return response({ name: '张老师', username: 'teacher1', department: '计算机学院', phone: '13800000000', email: 'teacher@example.com' });
    if (url.endsWith('/teacher/profile') && method === 'PUT') return response({ name: '张老师', username: 'teacher1', department: '计算机学院', phone: '13800000000', email: 'teacher@example.com' });
    if (url.endsWith('/teacher/password')) return response({ ok: true });
    return response({});
  });
}

function mockAdminApi(options = {}) {
  const departments = [
    { id: 1, name: '计算机学院' },
    { id: 2, name: '人工智能学院' },
  ];
  let teachers = [
    { id: 1, name: '张老师', username: 'teacher1', department_id: 1, department_name: '计算机学院', department: '计算机学院' },
    { id: 2, name: '李老师', username: 'teacher2', department_id: 2, department_name: '人工智能学院', department: '人工智能学院' },
  ];
  const baseStudents = [
    { id: 1, name: '测试学生', username: 'student1', student_no: '20230001', grade: '2023', department_id: 1, department_name: '计算机学院' },
    { id: 2, name: '课程学生', username: 'student2', student_no: '20230002', grade: '2023', department_id: 2, department_name: '人工智能学院' },
    { id: 3, name: '候选学生', username: 'student3', student_no: '20230003', grade: '2023', department_id: 2, department_name: '人工智能学院' },
  ];
  const gappedStudents = [
    { id: 1, name: '李同学', username: 'student1', student_no: '20230001', grade: '2023', department_id: 1, department_name: '计算机学院' },
    { id: 4, name: '黄同学', username: 'B22042131', student_no: 'B22042131', grade: '2022', department_id: 1, department_name: '计算机学院' },
  ];
  let students = options.manyStudents
    ? Array.from({ length: 13 }, (_, index) => ({
      id: index + 1,
      name: `批量学生${index + 1}`,
      username: `student${index + 1}`,
      student_no: `2023${String(index + 1).padStart(4, '0')}`,
      grade: '2023',
      department_id: index % 2 === 0 ? 1 : 2,
      department_name: index % 2 === 0 ? '计算机学院' : '人工智能学院',
    }))
    : options.gappedStudentIds ? gappedStudents : baseStudents;
  let coursesData = [
    { id: 1, name: '数据结构', code: 'DATA-01', department_id: 1, department_name: '计算机学院', weekday: '周一', start_time: '08:00', end_time: '09:40', location: '教学楼B-201', teacher_name: '张老师', term: '2025 秋季', student_count: 0 },
    { id: 2, name: '生成式 AI', code: 'GEN-AI', department_id: 2, department_name: '人工智能学院', weekday: '周二', start_time: '14:00', end_time: '15:40', location: '教一-301', teacher_name: '李老师', term: '2026 春季', student_count: 1 },
  ];
  let classrooms = [
    { id: 1, name: '教一-301', building: '教一', capacity: 80 },
    { id: 2, name: '实验楼101', building: '实验楼', capacity: 48 },
  ];
  let scheduleSlots = [
    {
      id: 7,
      course_id: 2,
      weekday: '周二',
      period: 3,
      teacher_id: 2,
      teacher_name: '李老师',
      classroom_id: 1,
      classroom_name: '教一-301',
      course_type: 'LECTURE',
    },
  ];
  let courseStudents = [students[1]];
  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method ?? 'GET';
    if (url.endsWith('/auth/login')) {
      return response({ token: 'admin-jwt', user: { id: 1, username: 'admin', role: 'ADMIN', displayName: '系统管理员' } });
    }
    if (url.endsWith('/admin/dashboard')) {
      return response({
        kpis: { studentTotal: 3, todayPresent: 3, todayAbsent: 1, todayLate: 0, courseTotal: 2, departmentTotal: 2 },
        trend: [
          { date: '2026-04-20', present: 2, absent: 0, late: 1 },
          { date: '2026-04-21', present: 3, absent: 0, late: 0 },
          { date: '2026-04-22', present: 2, absent: 1, late: 0 },
          { date: '2026-04-23', present: 3, absent: 0, late: 0 },
          { date: '2026-04-24', present: 3, absent: 0, late: 0 },
          { date: '2026-04-25', present: 2, absent: 1, late: 0 },
          { date: '2026-04-26', present: 3, absent: 1, late: 0 },
        ],
        distribution: { present: 18, absent: 3, late: 1, rate: 86 },
        courseAttendance: [
          { course_id: 1, course_name: '数据结构', present: 2, total: 2, absent: 0, late: 0 },
          { course_id: 2, course_name: '生成式 AI', present: 1, total: 2, absent: 1, late: 0 },
        ],
        recentActivities: [
          { id: 1, student_name: '课程学生', course_name: '生成式 AI', checked_in_at: '2026-04-26T08:00:00Z', status: 'PRESENT' },
        ],
      });
    }
    if (url.endsWith('/admin/departments')) return response(departments);
    if (url.endsWith('/admin/terms')) {
      if (options.termsNotFound) return errorResponse(404, { error: 'Not Found' });
      return response([
        { value: '2025-2026学年 秋季学期', label: '2025-2026学年 秋季学期' },
        { value: '2026 春季', label: '2026 春季' },
      ]);
    }
    if (url.endsWith('/admin/teachers') && method === 'GET') return response(teachers);
    if (url.endsWith('/admin/teachers/1') && method === 'PUT') {
      teachers = [{ ...teachers[0], name: '张老师更新' }, teachers[1]];
      return response(teachers[0]);
    }
    if (url.endsWith('/admin/teachers/1/reset-password') && method === 'POST') return response({ ok: true });
    if (url.endsWith('/admin/students') && method === 'GET') return response(students);
    if (url.endsWith('/admin/students') && method === 'POST') {
      const body = JSON.parse(options.body);
      const department = departments.find((item) => String(item.id) === String(body.departmentId));
      const student = {
        id: 4,
        name: body.name,
        username: body.username,
        student_no: body.studentNo,
        grade: body.grade,
        department_id: body.departmentId,
        department_name: department?.name ?? '',
      };
      students = [...students, student];
      return response(student);
    }
    if (url.endsWith('/admin/students/1') && method === 'PUT') {
      const body = JSON.parse(options.body);
      const department = departments.find((item) => String(item.id) === String(body.departmentId));
      students = students.map((student) => student.id === 1 ? {
        ...student,
        name: body.name,
        student_no: body.studentNo,
        grade: body.grade,
        department_id: body.departmentId,
        department_name: department?.name ?? student.department_name,
      } : student);
      return response(students[0]);
    }
    if (url.endsWith('/admin/classrooms') && method === 'GET') return response(classrooms);
    if (url.endsWith('/admin/classrooms') && method === 'POST') {
      const body = JSON.parse(options.body);
      const classroom = { id: 3, name: body.name, building: body.building, capacity: body.capacity };
      classrooms = [...classrooms, classroom];
      return response(classroom);
    }
    if (url.endsWith('/admin/courses') && method === 'GET') return response(coursesData);
    if (url.endsWith('/admin/courses') && method === 'POST') {
      const body = JSON.parse(options.body);
      const department = departments.find((item) => String(item.id) === String(body.departmentId));
      const course = {
        id: 3,
        name: body.name,
        code: body.code,
        department_id: body.departmentId,
        department_name: department?.name ?? '',
        teacher_name: null,
        term: null,
        student_count: 0,
      };
      coursesData = [...coursesData, course];
      return response(course);
    }
    if (url.endsWith('/admin/courses/2/students') && method === 'POST') {
      const body = JSON.parse(options.body);
      const student = students.find((item) => item.id === body.studentId);
      if (student && !courseStudents.some((item) => item.id === student.id)) courseStudents = [...courseStudents, student];
      return response({ id: body.studentId });
    }
    if (url.endsWith('/admin/courses/2/students/2') && method === 'DELETE') {
      courseStudents = courseStudents.filter((student) => student.id !== 2);
      return response({});
    }
    if (url.endsWith('/admin/courses/2/schedule-slots') && method === 'PUT') {
      const body = JSON.parse(options.body);
      if (body.period === 4) return errorResponse(409, { message: '该教室此节次已被占用' });
      const selectedTeacher = teachers.find((item) => Number(item.id) === Number(body.teacherId));
      const selectedClassroom = classrooms.find((item) => Number(item.id) === Number(body.classroomId));
      const slot = {
        id: body.id ?? 8,
        course_id: 2,
        weekday: body.weekday,
        period: body.period,
        teacher_id: body.teacherId,
        teacher_name: selectedTeacher?.name,
        classroom_id: body.classroomId,
        classroom_name: selectedClassroom?.name,
        course_type: body.courseType,
      };
      scheduleSlots = [...scheduleSlots.filter((item) => item.id !== slot.id), slot];
      return response(slot);
    }
    if (url.endsWith('/admin/courses/2/schedule-slots/7') && method === 'DELETE') {
      scheduleSlots = scheduleSlots.filter((slot) => slot.id !== 7);
      return response({});
    }
    if (url.endsWith('/admin/courses/2')) {
      return response({
        course: coursesData[1],
        schedule: { course_id: 2, weekday: '周二', start_time: '14:00', end_time: '15:40', location: '教一-301' },
        teacher: { id: 2, name: '李老师', teacher_id: 2, term: '2026 春季', department_name: '人工智能学院' },
        teachers: [{ id: 2, name: '李老师', teacher_id: 2, term: '2026 春季', department_name: '人工智能学院' }],
        scheduleSlots,
        students: courseStudents,
      });
    }
    return response({});
  });
}

function response(body) {
  return Promise.resolve({
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
  });
}

function errorResponse(status, body) {
  return Promise.resolve({
    ok: false,
    status,
    statusText: body.error ?? 'Error',
    json: async () => body,
  });
}
