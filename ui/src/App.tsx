import { useState, type ReactNode } from 'react';
import { Link, NavLink, Route, Routes } from 'react-router';
import WeeklyReportPage from '@/features/weeklyReport/WeeklyReportPage';
import RankingsPage from '@/features/rankings/RankingsPage';
import PlayerRankingsPage from '@/features/playerRankings/PlayerRankingsPage';
import RostersPage from '@/features/rosters/RostersPage';
import ScoreboardPage from '@/features/scoreboard/ScoreboardPage';
import LateRowBetsPage from '@/features/lateRowBets/LateRowBetsPage';
import RulesPage from '@/features/rules/RulesPage';
import AdminPage from '@/features/admin/AdminPage';
import { LeagueSeasonProvider } from '@/features/leagues/LeagueSeasonContext';
import { AuthProvider } from '@/features/auth/AuthContext';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { HeaderSlotProvider } from '@/shared/components/HeaderSlot';
import TabBar from './TabBar';

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

const navLinkTone = (isActive: boolean): string =>
  isActive ? 'bg-surface-card text-ink-strong' : ON_CHROME_IDLE_CLASS;

const desktopLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `inline-flex items-center ${TAP_TARGET_CLASS} px-3 rounded text-sm ${NAV_LABEL_CLASS} transition ${navLinkTone(isActive)}`;

function SiteFooter(): ReactNode {
  return (
    <footer className="hidden lg:block bg-chrome">
      <div className="max-w-screen-2xl mx-auto px-safe-3 sm:px-safe-4 py-1.5 flex justify-end">
        <Link
          to="/admin"
          className={`inline-flex items-center ${TAP_TARGET_CLASS} px-3 rounded text-sm ${NAV_LABEL_CLASS} ${ON_CHROME_IDLE_CLASS}`}
        >
          Admin
        </Link>
      </div>
    </footer>
  );
}

function App(): ReactNode {
  // State, not a ref: the pages portalling into the slot re-render once it mounts.
  const [headerSlot, setHeaderSlot] = useState<HTMLElement | null>(null);
  return (
    <AuthProvider>
    <LeagueSeasonProvider>
    <HeaderSlotProvider slot={headerSlot}>
      <div className="min-h-screen bg-canvas text-ink">
        <nav aria-label="Main" className="bg-chrome">
          <div className="max-w-screen-2xl mx-auto px-safe-3 sm:px-safe-4 min-h-14 flex justify-between gap-4">
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

            <div ref={setHeaderSlot} className="sm:hidden flex items-center min-w-0" />
          </div>
        </nav>

        <main
          className="max-w-screen-2xl mx-auto px-safe-3 sm:px-safe-4 pt-4 sm:pt-6 pb-above-tab-bar-4 sm:pb-above-tab-bar-6 lg:pb-6"
        >
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
        <TabBar />
      </div>
    </HeaderSlotProvider>
    </LeagueSeasonProvider>
    </AuthProvider>
  );
}

export default App;
