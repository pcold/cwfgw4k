import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthContext';
import LoginModal from '@/features/auth/LoginModal';
import CreateLeagueSection from './CreateLeagueSection';
import CreateSeasonSection from './CreateSeasonSection';
import UploadScheduleSection from './UploadScheduleSection';
import UploadRostersSection from './UploadRostersSection';
import ResetTournamentSection from './ResetTournamentSection';
import CleanSeasonSection from './CleanSeasonSection';
import DeleteSeasonSection from './DeleteSeasonSection';

function AdminPage() {
  const { authenticated, loading, username, logout } = useAuth();
  const navigate = useNavigate();

  if (loading) {
    return <p className="text-gray-400">Checking session…</p>;
  }

  if (!authenticated) {
    return (
      <>
        <p className="text-gray-400">You must be logged in to access the admin page.</p>
        <LoginModal open onClose={() => navigate('/')} />
      </>
    );
  }

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold">Admin</h2>
        <div className="flex items-center gap-3 text-sm text-gray-400">
          {username ? <span>Signed in as {username}</span> : null}
          <button
            type="button"
            onClick={() => {
              void logout();
            }}
            className="bg-gray-700 hover:bg-gray-600 text-white px-3 py-1.5 rounded text-sm"
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
