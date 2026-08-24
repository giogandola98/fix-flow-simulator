import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../i18n';
import TopBar from './TopBar';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { FIXSessionConfig, Scenario, ExecutionStatus } from '../types';

// Network is never exercised in these render-only tests, but mock to be safe.
vi.mock('../api/scenarios', () => ({
  executeScenario: vi.fn(), updateScenario: vi.fn(), importScenario: vi.fn(),
}));
vi.mock('../api/executions', () => ({ stopExecution: vi.fn() }));
vi.mock('../api/system', () => ({ shutdownSimulator: vi.fn() }));

const scenario: Scenario = {
  id: 's1', name: 'Sc', description: '', version: '1.0', sessionRef: '', nodeCount: 0,
};
const session = (connected: boolean): FIXSessionConfig => ({
  id: 'sess1', name: 'S', mode: 'INITIATOR', fixVersion: 'FIX_44', defaultApplVerID: '9',
  senderCompID: 'C', targetCompID: 'S', host: 'localhost', port: 9001, heartbeatInterval: 30,
  resetOnLogon: false, resetOnLogout: false, connected,
});

const renderTopBar = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <TopBar />
    </QueryClientProvider>,
  );

const setStores = (opts: { scenario?: Scenario | null; session?: FIXSessionConfig | null; status?: ExecutionStatus | 'IDLE' }) => {
  useScenarioStore.setState({ activeScenario: opts.scenario ?? null, nodes: [], edges: [], isDirty: false });
  useSessionStore.setState({ activeSession: opts.session ?? null });
  useExecutionStore.setState({ executionStatus: opts.status ?? 'IDLE', activeExecutionId: null });
};

describe('TopBar Run/Stop button state', () => {
  beforeEach(() => setStores({}));

  it('disables Run when there is no connected session', () => {
    setStores({ scenario, session: session(false), status: 'IDLE' });
    renderTopBar();
    expect(screen.getByRole('button', { name: 'Run' })).toBeDisabled();
  });

  it('enables Run when scenario + connected session and not running', () => {
    setStores({ scenario, session: session(true), status: 'IDLE' });
    renderTopBar();
    expect(screen.getByRole('button', { name: 'Run' })).toBeEnabled();
  });

  it('disables Run while a run is in progress, and enables Stop', () => {
    setStores({ scenario, session: session(true), status: 'RUNNING' });
    renderTopBar();
    expect(screen.getByRole('button', { name: 'Run' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeEnabled();
  });

  it('disables Stop when not running', () => {
    setStores({ scenario, session: session(true), status: 'IDLE' });
    renderTopBar();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeDisabled();
  });
});

describe('TopBar shutdown button', () => {
  beforeEach(() => setStores({}));

  it('asks for confirmation before shutting down', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderTopBar();
    await userEvent.click(screen.getByTestId('topbar-shutdown'));
    expect(confirm).toHaveBeenCalled();
    expect(screen.queryByTestId('shutdown-overlay')).toBeNull();
    confirm.mockRestore();
  });
});
