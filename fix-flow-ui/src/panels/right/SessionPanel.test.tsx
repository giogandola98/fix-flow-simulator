import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionPanel } from './SessionPanel';
import * as sessionsApi from '../../api/sessions';
import * as dictionariesApi from '../../api/dictionaries';
import { useSessionStore } from '../../store/sessionStore';

vi.mock('../../api/sessions');
vi.mock('../../api/dictionaries');
vi.mock('../../hooks/useSessionSubscription', () => ({ useSessionSubscription: () => {} }));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <SessionPanel />
    </QueryClientProvider>,
  );
}

describe('SessionPanel data dictionary', () => {
  beforeEach(() => {
    vi.mocked(sessionsApi.getSessions).mockResolvedValue([]);
    vi.mocked(dictionariesApi.getDictionaries).mockResolvedValue([
      { name: 'MY.xml', sizeBytes: 10 },
      { name: 'OTHER.xml', sizeBytes: 20 },
    ]);
    vi.mocked(sessionsApi.createSession).mockResolvedValue({
      id: 's-1', name: 'default', mode: 'INITIATOR', fixVersion: 'FIX_44', defaultApplVerID: '',
      senderCompID: 'CLIENT', targetCompID: 'SERVER', host: 'localhost', port: 9876,
      heartbeatInterval: 30, resetOnLogon: true, resetOnLogout: false, connected: false,
      appDataDictionary: 'MY.xml', transportDataDictionary: null,
    });
  });

  it('lists the stored dictionaries and saves the choice', async () => {
    renderPanel();
    await screen.findByRole('option', { name: 'MY.xml' });
    expect(screen.getByRole('option', { name: 'OTHER.xml' })).toBeInTheDocument();

    const select = screen.getByRole('option', { name: 'MY.xml' }).closest('select') as HTMLSelectElement;
    await userEvent.selectOptions(select, 'MY.xml');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(sessionsApi.createSession).toHaveBeenCalled());
    expect(vi.mocked(sessionsApi.createSession).mock.calls[0][0]).toMatchObject({
      appDataDictionary: 'MY.xml',
      transportDataDictionary: '',
    });
  });
});

describe('SessionPanel auto-connect', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useSessionStore.getState().setActiveSession(null);
  });

  it('saves the Connect at startup checkbox', async () => {
    vi.mocked(sessionsApi.getSessions).mockResolvedValue([]);
    vi.mocked(dictionariesApi.getDictionaries).mockResolvedValue([]);
    vi.mocked(sessionsApi.createSession).mockResolvedValue({
      id: 's-2', name: 'default', mode: 'INITIATOR', fixVersion: 'FIX_44', defaultApplVerID: '',
      senderCompID: 'CLIENT', targetCompID: 'SERVER', host: 'localhost', port: 9876,
      heartbeatInterval: 30, resetOnLogon: true, resetOnLogout: false, connected: false, autoConnect: true,
    });
    renderPanel();
    await userEvent.click(await screen.findByLabelText(/session.fields.autoConnect/));
    await userEvent.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(sessionsApi.createSession).toHaveBeenCalled());
    expect(vi.mocked(sessionsApi.createSession).mock.calls[0][0]).toMatchObject({ autoConnect: true });
  });
});
