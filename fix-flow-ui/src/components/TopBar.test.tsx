import { describe, it, expect, beforeEach, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../i18n';
import TopBar from './TopBar';
import { useScenarioStore } from '../store/scenarioStore';
import { useSessionStore } from '../store/sessionStore';
import { useExecutionStore } from '../store/executionStore';
import { shutdownSimulator } from '../api/system';
import { stopExecution } from '../api/executions';
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
  useExecutionStore.setState({
    executionStatus: opts.status ?? 'IDLE', activeExecutionId: null, executionIds: [], finishedStatuses: {},
  });
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
  beforeEach(() => {
    setStores({});
    vi.mocked(shutdownSimulator).mockClear();
  });

  it('does not shut down when the confirmation dialog is declined', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderTopBar();
    await userEvent.click(screen.getByTestId('topbar-shutdown'));
    expect(confirm).toHaveBeenCalled();
    expect(shutdownSimulator).not.toHaveBeenCalled();
    expect(screen.queryByTestId('shutdown-overlay')).toBeNull();
    confirm.mockRestore();
  });

  it('shuts down when the confirmation dialog is accepted', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderTopBar();
    await userEvent.click(screen.getByTestId('topbar-shutdown'));
    expect(confirm).toHaveBeenCalled();
    expect(shutdownSimulator).toHaveBeenCalledTimes(1);
    confirm.mockRestore();
  });
});

describe('TopBar all-rows run', () => {
  const startBatch = () => {
    setStores({ scenario, session: session(true), status: 'RUNNING' });
    useExecutionStore.setState({ activeExecutionId: 'r0', executionIds: ['r0', 'r1', 'r2'], finishedStatuses: {} });
  };

  beforeEach(() => vi.mocked(stopExecution).mockReset().mockResolvedValue(undefined));

  it('keeps Run disabled until the last row has finished', () => {
    startBatch();
    renderTopBar();
    const run = screen.getByRole('button', { name: 'Run' });
    act(() => useExecutionStore.getState().finishExecution('r0', 'PASSED', 't0'));
    expect(run).toBeDisabled();
    act(() => useExecutionStore.getState().finishExecution('r1', 'FAILED', 't1'));
    expect(run).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeEnabled();
    act(() => useExecutionStore.getState().finishExecution('r2', 'PASSED', 't2'));
    expect(run).toBeEnabled();
    expect(useExecutionStore.getState().executionStatus).toBe('FAILED');
  });

  it('Stop stops every row that has not finished, queued ones first', async () => {
    startBatch();
    useExecutionStore.getState().finishExecution('r0', 'PASSED', 't0');
    renderTopBar();
    await userEvent.click(screen.getByRole('button', { name: 'Stop' }));
    await waitFor(() => expect(stopExecution).toHaveBeenCalledTimes(2));
    expect(vi.mocked(stopExecution).mock.calls.map((c) => c[0])).toEqual(['r2', 'r1']);
    await waitFor(() => expect(useExecutionStore.getState().executionStatus).toBe('STOPPED'));
  });
});
