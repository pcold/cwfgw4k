import { NavLink, Route, Routes } from 'react-router-dom';
import WeeklyReportPage from './pages/WeeklyReportPage';

function App() {
  return (
    <div className="min-h-screen bg-gray-900 text-gray-100">
      <nav className="bg-gray-800 border-b border-gray-700">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
          <h1 className="text-lg font-bold text-green-400 tracking-wide">CWFG</h1>
          <div className="flex gap-1">
            <NavLink
              to="/report"
              className={({ isActive }) =>
                `px-3 py-1.5 rounded text-sm font-medium transition ${
                  isActive ? 'bg-gray-700 text-white' : 'text-gray-400 hover:text-white'
                }`
              }
            >
              Weekly Report
            </NavLink>
          </div>
        </div>
      </nav>

      <main className="max-w-6xl mx-auto px-4 py-6">
        <Routes>
          <Route path="/" element={<WeeklyReportPage />} />
          <Route path="/report" element={<WeeklyReportPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
