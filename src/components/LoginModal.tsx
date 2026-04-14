import { useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';

interface Props {
  open: boolean;
  onClose: () => void;
}

function LoginModal({ open, onClose }: Props) {
  const { login, loginError, loginPending, clearLoginError } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) {
      setPassword('');
      clearLoginError();
    }
  }, [open, clearLoginError]);

  if (!open) return null;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await login(username, password);
      setPassword('');
      onClose();
    } catch {
      // error surfaced via loginError
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="login-modal-title"
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
    >
      <div className="bg-gray-800 rounded-lg p-6 w-80 border border-gray-600">
        <h3 id="login-modal-title" className="text-lg font-bold mb-4">
          Admin Login
        </h3>
        <form onSubmit={onSubmit}>
          <div className="mb-3">
            <label className="block text-xs text-gray-400 mb-1" htmlFor="login-username">
              Username
            </label>
            <input
              id="login-username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full"
              placeholder="Username"
              autoFocus
            />
          </div>
          <div className="mb-4">
            <label className="block text-xs text-gray-400 mb-1" htmlFor="login-password">
              Password
            </label>
            <input
              id="login-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full"
              placeholder="Password"
            />
          </div>
          {loginError ? (
            <span role="alert" className="text-red-400 text-xs block mb-3">
              {loginError}
            </span>
          ) : null}
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={loginPending}
              className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium flex-1"
            >
              {loginPending ? '...' : 'Log In'}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="bg-gray-600 hover:bg-gray-500 text-white px-4 py-2 rounded text-sm font-medium"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default LoginModal;
