import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import ConnectScreen from './screens/ConnectScreen.jsx';
import ConfigureScreen from './screens/ConfigureScreen.jsx';
import RunScreen from './screens/RunScreen.jsx';

// Top-level shell + routing. Business logic lives in the backend; these screens
// only render forms/schemas and visualise the SSE stream (spec §11).
export default function App() {
  return (
    <div className="app">
      <header className="app__header">
        <span className="app__brand">⚡ ChaosInjector</span>
        <nav className="app__nav">
          <NavLink to="/connect">1 · Connect</NavLink>
          <NavLink to="/configure">2 · Configure</NavLink>
          <NavLink to="/run">3 · Run</NavLink>
        </nav>
      </header>
      <main className="app__main">
        <Routes>
          <Route path="/" element={<Navigate to="/connect" replace />} />
          <Route path="/connect" element={<ConnectScreen />} />
          <Route path="/configure" element={<ConfigureScreen />} />
          <Route path="/run" element={<RunScreen />} />
          <Route path="/run/:experimentId" element={<RunScreen />} />
        </Routes>
      </main>
    </div>
  );
}
