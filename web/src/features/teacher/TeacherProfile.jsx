import { useEffect, useState } from 'react';
import { KeyRound, LogOut, Save, UserRound } from 'lucide-react';

export function TeacherProfile({ client, logout }) {
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ phone: '', email: '' });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    client.get('/teacher/profile').then((data) => {
      if (!cancelled) {
        setProfile(data);
        setForm({ phone: data.phone ?? '', email: data.email ?? '' });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [client]);

  async function saveProfile(event) {
    event.preventDefault();
    setError('');
    const saved = await client.put('/teacher/profile', form);
    setProfile(saved);
    setMessage('资料已保存');
  }

  async function changePassword(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    if (passwords.newPassword !== passwords.confirmPassword) {
      setError('两次输入的新密码不一致');
      return;
    }
    await client.post('/teacher/password', {
      currentPassword: passwords.currentPassword,
      newPassword: passwords.newPassword,
    });
    setPasswords({ currentPassword: '', newPassword: '', confirmPassword: '' });
    setMessage('密码已修改');
  }

  if (!profile) return <div className="panel">加载中</div>;

  return (
    <div className="teacherPage">
      <section className="pageHead">
        <div>
          <h1>个人中心</h1>
          <p>维护联系方式和账号密码。</p>
        </div>
        <button className="ghost" onClick={logout}><LogOut size={16} />退出登录</button>
      </section>
      <section className="profileGrid">
        <form className="panel profileCard" onSubmit={saveProfile}>
          <div className="panelHead"><h2><UserRound size={17} />基本资料</h2></div>
          <label>
            工号
            <input value={profile.username ?? ''} readOnly />
          </label>
          <label>
            姓名
            <input value={profile.name ?? profile.display_name ?? ''} readOnly />
          </label>
          <label>
            院系
            <input value={profile.department ?? ''} readOnly />
          </label>
          <label>
            手机号
            <input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
          </label>
          <label>
            邮箱
            <input type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
          </label>
          <button><Save size={16} />保存资料</button>
        </form>
        <form className="panel profileCard" onSubmit={changePassword}>
          <div className="panelHead"><h2><KeyRound size={17} />修改密码</h2></div>
          <label>
            当前密码
            <input type="password" value={passwords.currentPassword} onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })} />
          </label>
          <label>
            新密码
            <input type="password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} />
          </label>
          <label>
            确认新密码
            <input type="password" value={passwords.confirmPassword} onChange={(event) => setPasswords({ ...passwords, confirmPassword: event.target.value })} />
          </label>
          <button>修改密码</button>
          {error && <div className="error">{error}</div>}
          {message && <div className="success">{message}</div>}
        </form>
      </section>
    </div>
  );
}
