import { useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '@/features/auth/AuthContext';

interface Props {
  open: boolean;
  onClose: () => void;
}

function LoginModal({ open, onClose }: Props) {
  const { login, loginError, loginPending } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  // Subscribe to keyboard Escape — a real side effect on a non-React event source.
  // The "reset on close" effect was removed: the parent unmounts this modal when
  // it isn't needed, so component state (and loginError via the auth context)
  // resets via remount rather than via a useEffect that syncs prop → state.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

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
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-3 sm:p-4"
    >
      <div className="bg-gray-800 rounded-lg p-6 w-[min(92vw,22rem)] border border-gray-600">
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
