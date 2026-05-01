import { BarChart3, BookOpen, Building2, Landmark, UserRound, UsersRound } from 'lucide-react';

export const adminResources = {
  students: {
    title: '学生管理',
    endpoint: '/admin/students',
    icon: UserRound,
    fields: [
      ['name', '姓名', { required: true, placeholder: '请输入学生姓名' }],
      ['username', '账号', { required: true, placeholder: '请输入登录账号' }],
      ['password', '初始密码', { placeholder: '留空则使用默认密码 (123456)' }],
      ['studentNo', '学号', { required: true, placeholder: '请输入学号' }],
      ['grade', '年级', { required: true, type: 'year' }],
      ['departmentId', '所属院系', { required: true }],
    ],
    columns: ['display_index', 'name', 'username', 'student_no', 'department_name', 'grade'],
  },
  teachers: {
    title: '教师管理',
    endpoint: '/admin/teachers',
    icon: UsersRound,
    fields: [
      ['name', '姓名', { required: true, placeholder: '请输入教师姓名' }],
      ['username', '账号', { required: true, placeholder: '请输入登录账号' }],
      ['password', '初始密码', { placeholder: '留空则使用默认密码 (123456)' }],
      ['departmentId', '所属院系', { required: true }],
    ],
    columns: ['display_index', 'name', 'username', 'department_name'],
  },
  classrooms: {
    title: '教室管理',
    endpoint: '/admin/classrooms',
    icon: Building2,
    fields: [
      ['name', '教室名称'],
      ['building', '教学楼'],
      ['capacity', '容量'],
    ],
    columns: ['display_index', 'name', 'building', 'capacity'],
    labels: { name: '教室名称' },
  },
};

export const adminNav = [
  ['dashboard', '数据总览', BarChart3],
  ['students', '学生管理', UserRound],
  ['teachers', '教师管理', UsersRound],
  ['classrooms', '教室管理', Building2],
  ['courses', '课程管理', BookOpen],
  ['departments', '院系管理', Landmark],
];

export const adminLabels = {
  id: 'ID',
  display_index: '序号',
  name: '姓名',
  username: '账号',
  department: '院系',
  department_name: '所属院系',
  student_no: '学号',
  class_name: '班级',
  grade: '年级',
  code: '课程代码',
  course_name: '课程',
  teacher_name: '教师',
  term: '学期',
  building: '教学楼',
  capacity: '容量',
  student_name: '学生',
  source: '来源',
  status: '状态',
  checked_in_at: '签到时间',
  session_id: '场次',
  total: '应到',
  present: '已到',
  excused: '请假',
  absent: '缺勤',
  reason: '原因',
  created_at: '提交时间',
};
