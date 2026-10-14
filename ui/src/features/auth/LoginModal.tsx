import { useState, type FormEvent, type ReactNode } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import FormModal from '@/shared/components/FormModal';
import { TextField } from '@/shared/components/formFields';

interface Props {
  onClose: () => void;
}

function LoginModal({ onClose }: Props): ReactNode {
  const { login, loginError, loginPending } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  // Success is not a close: the parent unmounts this modal once authenticated, and its onClose is the
  // cancel path, which on AdminPage leaves for the home page.
  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await login(username, password);
    } catch {
      // error surfaced via loginError
    }
  };

  return (
    <FormModal
      title="Admin Login"
      submitLabel={loginPending ? '...' : 'Log In'}
      isPending={loginPending}
      serverError={loginError ?? undefined}
      onSubmit={(e) => void onSubmit(e)}
      onClose={onClose}
    >
      <TextField
        id="login-username"
        label="Username"
        value={username}
        error={undefined}
        placeholder="Username"
        initialFocus
        onChange={setUsername}
      />
      <TextField
        id="login-password"
        label="Password"
        type="password"
        value={password}
        error={undefined}
        placeholder="Password"
        onChange={setPassword}
      />
    </FormModal>
  );
}

export default LoginModal;
