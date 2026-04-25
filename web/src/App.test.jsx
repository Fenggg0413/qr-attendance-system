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

  await waitFor(() => expect(screen.getByRole('heading', { name: '我的课程' })).toBeInTheDocument());
  expect(screen.getByText(/张老师/)).toBeInTheDocument();
  expect(global.fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' }));
});

test('teacher can filter courses and open detail tabs', async () => {
  mockTeacherApi();

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
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

test('teacher can start attendance and view record drawer', async () => {
  mockTeacherApi();

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));
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

  await userEvent.click(screen.getByRole('button', { name: '关闭' }));
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

test('admin workspace supports navigation search edit enrollments and stats', async () => {
  mockAdminApi();

  render(<App />);
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  await waitFor(() => expect(screen.getByRole('button', { name: '教师管理' })).toBeInTheDocument());
  expect(screen.getByRole('button', { name: '选课名单' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '考勤统计' })).toBeInTheDocument();

  await waitFor(() => expect(screen.getByRole('heading', { name: '教师管理' })).toBeInTheDocument());
  await userEvent.type(screen.getByPlaceholderText('搜索当前表格'), '张');
  expect(screen.getByText('张老师')).toBeInTheDocument();
  expect(screen.queryByText('李老师')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '编辑 教师 张老师' }));
  await userEvent.clear(screen.getByLabelText('姓名'));
  await userEvent.type(screen.getByLabelText('姓名'), '张老师更新');
  await userEvent.click(screen.getByRole('button', { name: '保存' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/teachers/1', expect.objectContaining({ method: 'PUT' })));

  await userEvent.click(screen.getByRole('button', { name: '选课名单' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '选课名单' })).toBeInTheDocument());
  await userEvent.type(screen.getByLabelText('分配 ID'), '1');
  await userEvent.type(screen.getByLabelText('学生 ID'), '1');
  await userEvent.click(screen.getByRole('button', { name: '新增' }));
  await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/admin/enrollments', expect.objectContaining({ method: 'POST' })));

  await userEvent.click(screen.getByRole('button', { name: '考勤统计' }));
  await waitFor(() => expect(screen.getByRole('heading', { name: '考勤统计' })).toBeInTheDocument());
  expect(screen.getByText('应到')).toBeInTheDocument();
  expect(screen.getByText('Java Web 开发')).toBeInTheDocument();
  expect(screen.getByText('2')).toBeInTheDocument();
});

function mockTeacherApi() {
  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method ?? 'GET';
    if (url.endsWith('/auth/login')) {
      return response({ token: 'jwt', user: { id: 10, username: 'teacher1', role: 'TEACHER', displayName: '张老师' } });
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

function mockAdminApi() {
  let teachers = [
    { id: 1, name: '张老师', username: 'teacher1', department: '计算机学院' },
    { id: 2, name: '李老师', username: 'teacher2', department: '数学学院' },
  ];
  let enrollments = [];
  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method ?? 'GET';
    if (url.endsWith('/auth/login')) {
      return response({ token: 'admin-jwt', user: { id: 1, username: 'admin', role: 'ADMIN', displayName: '系统管理员' } });
    }
    if (url.endsWith('/admin/teachers') && method === 'GET') return response(teachers);
    if (url.endsWith('/admin/teachers/1') && method === 'PUT') {
      teachers = [{ ...teachers[0], name: '张老师更新' }, teachers[1]];
      return response(teachers[0]);
    }
    if (url.endsWith('/admin/students')) return response([{ id: 1, name: '李同学', username: 'student1', student_no: '20230001', class_id: 1, class_name: '软件 2301' }]);
    if (url.endsWith('/admin/classes')) return response([{ id: 1, name: '软件 2301', grade: '2023' }]);
    if (url.endsWith('/admin/courses')) return response([{ id: 1, name: 'Java Web 开发', code: 'JAVA-WEB-01', class_id: 1, class_name: '软件 2301' }]);
    if (url.endsWith('/admin/course-assignments')) return response([{ id: 1, course_id: 1, course_name: 'Java Web 开发', teacher_id: 1, teacher_name: '张老师', term: '2025-2026 第二学期' }]);
    if (url.endsWith('/admin/enrollments') && method === 'GET') return response(enrollments);
    if (url.endsWith('/admin/enrollments') && method === 'POST') {
      enrollments = [{ id: 1, assignment_id: 1, course_name: 'Java Web 开发', student_id: 1, student_name: '李同学', student_no: '20230001' }];
      return response(enrollments[0]);
    }
    if (url.endsWith('/admin/leave-requests')) return response([{ id: 1, session_id: 1, student_name: '李同学', reason: '病假', status: 'PENDING' }]);
    if (url.endsWith('/admin/attendance-records')) return response([{ id: 1, session_id: 1, course_name: 'Java Web 开发', student_name: '李同学', status: 'PRESENT', source: 'QR', checked_in_at: '2026-04-26T08:00:00Z' }]);
    if (url.endsWith('/admin/statistics')) return response([{ course_name: 'Java Web 开发', status: 'PRESENT', count: 1 }]);
    if (url.endsWith('/admin/attendance-stats')) return response([{ session_id: 1, course_name: 'Java Web 开发', teacher_name: '张老师', class_name: '软件 2301', total: 2, present: 1, excused: 0, absent: 1 }]);
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
