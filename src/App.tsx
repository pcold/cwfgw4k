import { NavLink, Route, Routes } from 'react-router-dom';
import WeeklyReportPage from './pages/WeeklyReportPage';
import RankingsPage from './pages/RankingsPage';
import RostersPage from './pages/RostersPage';
import ScoreboardPage from './pages/ScoreboardPage';
import LateRowBetsPage from './pages/LateRowBetsPage';
import RulesPage from './pages/RulesPage';
import AdminPage from './pages/AdminPage';
import LeagueSeasonPicker from './components/LeagueSeasonPicker';
import { LeagueSeasonProvider } from './context/LeagueSeasonContext';
import { AuthProvider } from './context/AuthContext';

const navLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `px-3 py-1.5 rounded text-sm font-medium transition ${
    isActive ? 'bg-gray-700 text-white' : 'text-gray-400 hover:text-white'
  }`;

function App() {
  return (
    <AuthProvider>
    <LeagueSeasonProvider>
      <div className="min-h-screen bg-gray-900 text-gray-100">
        <nav className="bg-gray-800 border-b border-gray-700">
          <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
            <h1 className="text-lg font-bold text-green-400 tracking-wide">CWFG</h1>
            <div className="flex gap-1">
              <NavLink to="/scoreboard" className={navLinkClass}>
                Scoreboard
              </NavLink>
              <NavLink to="/report" className={navLinkClass}>
                Weekly Report
              </NavLink>
              <NavLink to="/rankings" className={navLinkClass}>
                Team Standings
              </NavLink>
              <NavLink to="/rosters" className={navLinkClass}>
                Rosters
              </NavLink>
              <NavLink to="/late-row-bets" className={navLinkClass}>
                Late Row Bets
              </NavLink>
              <NavLink to="/rules" className={navLinkClass}>
                Rules
              </NavLink>
              <NavLink to="/admin" className={navLinkClass}>
                Admin
              </NavLink>
            </div>
          </div>
        </nav>

        <LeagueSeasonPicker />

        <main className="max-w-6xl mx-auto px-4 py-6">
          <Routes>
            <Route path="/" element={<ScoreboardPage />} />
            <Route path="/scoreboard" element={<ScoreboardPage />} />
            <Route path="/report" element={<WeeklyReportPage />} />
            <Route path="/rankings" element={<RankingsPage />} />
            <Route path="/rosters" element={<RostersPage />} />
            <Route path="/late-row-bets" element={<LateRowBetsPage />} />
            <Route path="/rules" element={<RulesPage />} />
            <Route path="/admin" element={<AdminPage />} />
          </Routes>
        </main>
      </div>
    </LeagueSeasonProvider>
    </AuthProvider>
  );
}

export default App;
