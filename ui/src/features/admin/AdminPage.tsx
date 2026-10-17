import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '@/features/auth/AuthContext';
import LoginModal from '@/features/auth/LoginModal';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import PageTitle from '@/shared/components/PageTitle';
import CreateLeagueSection from './CreateLeagueSection';
import CreateSeasonSection from './CreateSeasonSection';
import UploadScheduleSection from './UploadScheduleSection';
import UploadRostersSection from './UploadRostersSection';
import ResetTournamentSection from './ResetTournamentSection';
import CleanSeasonSection from './CleanSeasonSection';
import DeleteSeasonSection from './DeleteSeasonSection';
import DataHealthSection from './DataHealthSection';

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
      <PageTitle
        title="Admin"
        subtitle={username ? `Signed in as ${username}` : undefined}
        actions={
          <button
            type="button"
            onClick={() => {
              void logout();
            }}
            className={`bg-surface-raised hover:bg-surface-raised-hover text-ink-strong px-3 py-1.5 rounded text-sm ${TAP_TARGET_CLASS}`}
          >
            Log out
          </button>
        }
      />
      <CreateLeagueSection />
      <CreateSeasonSection />
      <UploadScheduleSection />
      <UploadRostersSection />
      <ResetTournamentSection />
      <CleanSeasonSection />
      <DeleteSeasonSection />
      <DataHealthSection />
    </section>
  );
}

export default AdminPage;
