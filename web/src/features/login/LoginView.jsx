import { useState } from 'react';
import { ClipboardCheck, Eye, EyeOff, KeyRound, Loader2, UserRound, X } from 'lucide-react';
import { api, ApiError } from '../../services/api';

export function LoginView({ onLogin }) {
  const [form, setForm] = useState({ username: 'admin', password: 'admin123' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      onLogin(await api.login(form));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login">
      <form className="loginPanel" onSubmit={submit}>
        <div className="loginBrand">
          <div className="loginBrandIcon">
            <ClipboardCheck size={24} strokeWidth={2.2} />
          </div>
          <h1>校园云考勤系统</h1>
        </div>

        {error && (
          <div className="loginBannerError" role="alert" aria-live="assertive">
            <X size={16} />
            <span>{error}</span>
          </div>
        )}

        <div className="loginFieldsGroup">
          <label className="loginField" htmlFor="login-username">
            <span>账号</span>
            <div className="loginInputWrap">
              <UserRound size={16} />
              <input
                id="login-username"
                name="username"
                autoComplete="username"
                required
                autoFocus
                value={form.username}
                placeholder="请输入学号或工号"
                onChange={(e) => setForm({ ...form, username: e.target.value })}
              />
            </div>
          </label>

          <label className="loginField" htmlFor="login-password">
            <span>密码</span>
            <div className="loginInputWrap">
              <KeyRound size={16} />
              <input
                id="login-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={form.password}
                placeholder="请输入密码"
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
              <button
                type="button"
                className="passwordToggle"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? "隐藏密码" : "显示密码"}
                title={showPassword ? "隐藏密码" : "显示密码"}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </label>
        </div>

        <div className="loginOptions">
          <label className="rememberMe">
            <input type="checkbox" name="remember" />
            <span>保持登录状态</span>
          </label>
          <button
            type="button"
            className="forgotPassword"
            onClick={() => alert('请联系系统管理员重置密码。')}
          >
            忘记密码？
          </button>
        </div>

        <button className="loginSubmit" disabled={loading}>
          {loading ? (
            <>
              <Loader2 size={18} className="loginSpin" />
              登录中...
            </>
          ) : (
            '登录系统'
          )}
        </button>
      </form>
    </main>
  );
}
