#!/usr/bin/env node

const defaults = {
  apiBase: process.env.API_BASE ?? 'http://localhost:8080/api',
  username: process.env.ADMIN_USERNAME ?? 'admin',
  password: process.env.ADMIN_PASSWORD ?? 'admin123',
  preset: process.env.DEMO_PRESET ?? 'medium',
};

function parseArgs(argv) {
  const options = { ...defaults };
  for (let index = 0; index < argv.length; index++) {
    const arg = argv[index];
    const next = argv[index + 1];
    if (arg === '--api-base' && next) {
      options.apiBase = next;
      index++;
    } else if (arg === '--username' && next) {
      options.username = next;
      index++;
    } else if (arg === '--password' && next) {
      options.password = next;
      index++;
    } else if (arg === '--preset' && next) {
      options.preset = next;
      index++;
    } else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    }
  }
  options.apiBase = options.apiBase.replace(/\/$/, '');
  return options;
}

function printHelp() {
  console.log(`Usage: node scripts/reset-campus-demo.mjs [options]

Options:
  --api-base <url>   API base URL, default http://localhost:8080/api
  --username <name>  Admin username, default admin
  --password <pass>  Admin password, default admin123
  --preset <name>    Demo preset: medium or small, default medium

Environment variables:
  API_BASE, ADMIN_USERNAME, ADMIN_PASSWORD, DEMO_PRESET`);
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = body?.message ?? body?.error ?? response.statusText;
    throw new Error(`${response.status} ${message}`);
  }
  return body;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const login = await request(`${options.apiBase}/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username: options.username, password: options.password }),
  });
  const result = await request(`${options.apiBase}/admin/demo-data/reset`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${login.token}` },
    body: JSON.stringify({ preset: options.preset }),
  });

  console.log('校园演示数据已重建');
  console.log(`规模: ${result.departments} 个学院, ${result.classes} 个班级, ${result.teachers} 名教师, ${result.students} 名学生, ${result.courses} 门课程`);
  console.log(`考勤: ${result.attendanceSessions} 场考勤, ${result.attendanceRecords} 条记录, ${result.leaveRequests} 条请假`);
  console.log(`教师示例: ${result.sampleTeacher.username} / ${result.sampleTeacher.password} (${result.sampleTeacher.name})`);
  console.log(`学生示例: ${result.sampleStudent.username} / ${result.sampleStudent.password} (${result.sampleStudent.name})`);
}

main().catch((error) => {
  console.error(`导入失败: ${error.message}`);
  process.exit(1);
});
