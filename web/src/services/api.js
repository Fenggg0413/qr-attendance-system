export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

const API_BASE = import.meta.env.VITE_API_BASE ?? '/api';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...(options.headers ?? {}),
    },
  });
  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      message = body.message ?? body.error ?? message;
    } catch {
      // 空响应保持 HTTP 状态文本。
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  login(credentials) {
    return request('/auth/login', { method: 'POST', body: JSON.stringify(credentials) });
  },
  withToken(token) {
    return {
      get: (path) => request(path, { token }),
      post: (path, body) => request(path, { method: 'POST', token, body: JSON.stringify(body) }),
      put: (path, body) => request(path, { method: 'PUT', token, body: JSON.stringify(body) }),
      delete: (path) => request(path, { method: 'DELETE', token }),
    };
  },
};
