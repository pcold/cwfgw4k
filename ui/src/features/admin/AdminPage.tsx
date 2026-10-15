import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '@/features/auth/AuthContext';
import LoginModal from '@/features/auth/LoginModal';
import CreateLeagueSection from './CreateLeagueSection';
import CreateSeasonSection from './CreateSeasonSection';
import UploadScheduleSection from './UploadScheduleSection';
import UploadRostersSection from './UploadRostersSection';
import ResetTournamentSection from './ResetTournamentSection';
import CleanSeasonSection from './CleanSeasonSection';
import DeleteSeasonSection from './DeleteSeasonSection';

function AdminPage(): ReactNode {
  const { authenticated, loading, username, logout } = useAuth();
  const navigate = useNavigate();

  if (loading) {
    return <p className="text-ink-muted">Checking session…</p>;
  }

  if (!authenticated) {
    return (
      <>
        <p className="text-ink-muted">You must be logged in to access the admin page.</p>
        <LoginModal onClose={() => void navigate('/')} />
      </>
    );
  }

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold">Admin</h2>
        <div className="flex items-center gap-3 text-sm text-ink-muted">
          {username ? <span>Signed in as {username}</span> : null}
          <button
            type="button"
            onClick={() => {
              void logout();
            }}
            className="bg-surface-raised hover:bg-surface-raised-hover text-ink-strong px-3 py-1.5 rounded text-sm"
          >
            Log out
          </button>
        </div>
      </div>
      <CreateLeagueSection />
      <CreateSeasonSection />
      <UploadScheduleSection />
      <UploadRostersSection />
      <ResetTournamentSection />
      <CleanSeasonSection />
      <DeleteSeasonSection />
    </section>
  );
}

export default AdminPage;
