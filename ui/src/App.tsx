import { useState, useEffect, type ReactNode } from 'react';
import { Link, NavLink, Route, Routes, useLocation } from 'react-router';
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
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';

const NAV_ITEMS: Array<{ to: string; label: string }> = [
  { to: '/scoreboard', label: 'Scoreboard' },
  { to: '/report', label: 'Report' },
  { to: '/rankings', label: 'Standings' },
  { to: '/player-rankings', label: 'Players' },
  { to: '/rosters', label: 'Rosters' },
  { to: '/late-row-bets', label: 'Bets' },
  { to: '/rules', label: 'Rules' },
];

const ON_CHROME_IDLE_CLASS = 'text-on-chrome-muted hover:text-on-chrome';
const NAV_LABEL_CLASS = 'font-display font-semibold uppercase tracking-wider';

const MENU_BUTTON_CLASS =
  `lg:hidden self-center inline-flex items-center justify-center w-11 h-11 rounded ${ON_CHROME_IDLE_CLASS}`;

const navLinkTone = (isActive: boolean): string =>
  isActive ? 'bg-surface-card text-ink-strong' : ON_CHROME_IDLE_CLASS;

const desktopLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `inline-flex items-center ${TAP_TARGET_CLASS} px-3 rounded text-sm ${NAV_LABEL_CLASS} transition ${navLinkTone(isActive)}`;

// `py-3` on a `text-base` line clears 44px by itself, so the menu's links need no TAP_TARGET_CLASS.
const mobileLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `block px-4 py-3 rounded text-base ${NAV_LABEL_CLASS} transition ${navLinkTone(isActive)}`;

function SiteFooter(): ReactNode {
  return (
    <footer className="border-t border-hairline bg-surface-card">
      <div className="max-w-6xl mx-auto px-4 py-3 flex justify-end">
        <Link
          to="/admin"
          className={`inline-flex items-center ${TAP_TARGET_CLASS} text-sm font-medium text-ink-muted hover:text-ink-strong`}
        >
          Admin
        </Link>
      </div>
    </footer>
  );
}

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
        <nav className="bg-chrome">
          <div className="max-w-screen-2xl mx-auto px-3 sm:px-4 min-h-14 flex justify-between gap-4">
            {/* The block's skew would slant the italic face twice, so the text is skewed back. */}
            <h1 className="flex items-center px-5 bg-primary -skew-x-12">
              <span className="skew-x-12 font-display italic font-extrabold text-xl uppercase tracking-wide text-on-primary">
                Castlewood FG
              </span>
            </h1>

            <div className="hidden lg:flex items-center gap-1">
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
              className="lg:hidden"
            >
              <div className="max-w-screen-2xl mx-auto px-3 sm:px-4 pb-3 flex flex-col gap-1">
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

        <SiteFooter />
      </div>
    </LeagueSeasonProvider>
    </AuthProvider>
  );
}

export default App;
