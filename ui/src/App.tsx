import { useState, useEffect, type ReactNode } from 'react';
import { NavLink, Route, Routes, useLocation } from 'react-router';
import WeeklyReportPage from '@/features/weeklyReport/WeeklyReportPage';
import RankingsPage from '@/features/rankings/RankingsPage';
import PlayerRankingsPage from '@/features/playerRankings/PlayerRankingsPage';
import RostersPage from '@/features/rosters/RostersPage';
import ScoreboardPage from '@/features/scoreboard/ScoreboardPage';
import LateRowBetsPage from '@/features/lateRowBets/LateRowBetsPage';
import RulesPage from '@/features/rules/RulesPage';
import AdminPage from '@/features/admin/AdminPage';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import { LeagueSeasonProvider } from '@/features/leagues/LeagueSeasonContext';
import { AuthProvider } from '@/features/auth/AuthContext';

const NAV_ITEMS: Array<{ to: string; label: string }> = [
  { to: '/scoreboard', label: 'Scoreboard' },
  { to: '/report', label: 'Weekly Report' },
  { to: '/rankings', label: 'Team Standings' },
  { to: '/player-rankings', label: 'Player Rankings' },
  { to: '/rosters', label: 'Rosters' },
  { to: '/late-row-bets', label: 'Late Row Bets' },
  { to: '/rules', label: 'Rules' },
  { to: '/admin', label: 'Admin' },
];

const MENU_BUTTON_CLASS =
  'lg:hidden inline-flex items-center justify-center w-11 h-11 rounded ' +
  'text-ink-secondary hover:text-ink-strong hover:bg-surface-raised';

const desktopLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `px-3 py-2 rounded text-sm font-medium transition ${
    isActive ? 'bg-surface-raised text-ink-strong' : 'text-ink-muted hover:text-ink-strong'
  }`;

const mobileLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `block px-4 py-3 rounded text-base font-medium transition ${
    isActive ? 'bg-surface-raised text-ink-strong' : 'text-ink-secondary hover:bg-surface-raised/50 hover:text-ink-strong'
  }`;

function App(): ReactNode {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const location = useLocation();

  // Close the mobile menu whenever the user navigates so it doesn't linger
  // over the next page after a link tap.
  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  return (
    <AuthProvider>
    <LeagueSeasonProvider>
      <div className="min-h-screen bg-canvas text-ink">
        <nav className="bg-surface-card border-b border-hairline">
          <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between gap-4">
            <h1 className="text-lg font-bold text-primary-ink tracking-wide">CWFG</h1>

            <div className="hidden lg:flex gap-1">
              {NAV_ITEMS.map((item) => (
                <NavLink key={item.to} to={item.to} className={desktopLinkClass}>
                  {item.label}
                </NavLink>
              ))}
            </div>

            <button
              type="button"
              className={MENU_BUTTON_CLASS}
              aria-label={mobileNavOpen ? 'Close navigation menu' : 'Open navigation menu'}
              aria-expanded={mobileNavOpen}
              aria-controls="mobile-nav"
              onClick={() => setMobileNavOpen((open) => !open)}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="w-6 h-6"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
                aria-hidden="true"
              >
                {mobileNavOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 7h16M4 12h16M4 17h16" />
                )}
              </svg>
            </button>
          </div>

          {mobileNavOpen ? (
            <div
              id="mobile-nav"
              className="lg:hidden border-t border-hairline bg-surface-card"
            >
              <div className="max-w-6xl mx-auto px-2 py-2 flex flex-col gap-1">
                {NAV_ITEMS.map((item) => (
                  <NavLink key={item.to} to={item.to} className={mobileLinkClass}>
                    {item.label}
                  </NavLink>
                ))}
              </div>
            </div>
          ) : null}
        </nav>

        <LeagueSeasonPicker />

        <main className="max-w-screen-2xl mx-auto px-3 sm:px-4 py-4 sm:py-6">
          <Routes>
            <Route path="/" element={<ScoreboardPage />} />
            <Route path="/scoreboard" element={<ScoreboardPage />} />
            <Route path="/report" element={<WeeklyReportPage />} />
            <Route path="/rankings" element={<RankingsPage />} />
            <Route path="/player-rankings" element={<PlayerRankingsPage />} />
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
