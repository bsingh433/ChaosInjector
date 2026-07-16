import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import { AppStateProvider } from './state/AppState.jsx';

// The Connect screen fetches /api/version on mount; stub fetch so the smoke
// test does not need a live backend.
vi.stubGlobal(
  'fetch',
  vi.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ name: 'ChaosInjector', version: '0.1.0' }),
    }),
  ),
);

describe('App shell', () => {
  it('renders the brand and nav', () => {
    render(
      <MemoryRouter initialEntries={['/connect']}>
        <AppStateProvider>
          <App />
        </AppStateProvider>
      </MemoryRouter>,
    );
    expect(screen.getByText(/ChaosInjector/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Connect/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^Connect$/i })).toBeInTheDocument();
  });
});
