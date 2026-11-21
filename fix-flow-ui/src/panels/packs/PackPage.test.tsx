import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../../i18n';
import PackPage from './PackPage';
import { usePackStore } from '../../store/packStore';
import * as api from '../../api/packs';

vi.mock('../../api/packs');

const inspection: api.PackInspection = {
  id: 'demo', name: 'Demo pack', version: '1.0.0', provider: 'Demo', engineMin: '0.8.0',
  description: 'Generic pack',
  values: [
    { name: 'SENDER', description: 'SenderCompID of the simulator', required: true },
    { name: 'TARGET', description: 'TargetCompID of the system under test', required: true },
    { name: 'PORT', description: 'Port the acceptor listens on', default: '9876', required: false },
  ],
};

const installed: api.InstalledPack = {
  id: 'demo', name: 'Demo pack', version: '1.0.0', provider: 'Demo', description: 'Generic pack',
  installedAt: '2026-11-21T10:00:00Z', updatedAt: '2026-11-21T10:00:00Z',
  counts: { scenarios: 2, datasets: 1, sessions: 1, dictionaries: 1 },
};

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PackPage />
    </QueryClientProvider>,
  );

const zip = () => new File(['PK'], 'demo.zip', { type: 'application/zip' });

describe('PackPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    usePackStore.setState({ isOpen: true });
    vi.mocked(api.getPacks).mockResolvedValue([]);
    vi.mocked(api.inspectPack).mockResolvedValue(inspection);
    vi.mocked(api.packExportUrl).mockReturnValue('/x');
  });

  it('import form shows the values of the manifest', async () => {
    renderPage();
    const file = zip();
    await userEvent.upload(screen.getByTestId('pack-file'), file);

    expect(api.inspectPack).toHaveBeenCalledWith(file);
    expect(await screen.findByLabelText(/SENDER/)).toBeInTheDocument();
    expect(screen.getByLabelText(/TARGET/)).toBeInTheDocument();
    expect(screen.getByLabelText(/PORT/)).toHaveValue('9876');
    expect(screen.getByText('SenderCompID of the simulator')).toBeInTheDocument();
    expect(screen.getByText(/Demo pack 1\.0\.0/)).toBeInTheDocument();
  });

  it('install sends the typed values and shows the result', async () => {
    vi.mocked(api.importPack).mockResolvedValue({
      packId: 'demo', version: '1.0.0', scenarios: 2, datasets: 1, sessions: 1, dictionaries: 1,
      warnings: ['value X is not declared in pack.yaml; ignored'],
    });
    renderPage();
    const file = zip();
    await userEvent.upload(screen.getByTestId('pack-file'), file);
    await userEvent.type(await screen.findByLabelText(/SENDER/), 'SIM');
    await userEvent.type(screen.getByLabelText(/TARGET/), 'SUT');
    await userEvent.click(screen.getByRole('button', { name: 'Install' }));

    await waitFor(() => expect(api.importPack).toHaveBeenCalledWith(file,
      { SENDER: 'SIM', TARGET: 'SUT', PORT: '9876' }));
    expect(await screen.findByText(/2 scenarios/)).toBeInTheDocument();
    expect(screen.getByText(/value X is not declared/)).toBeInTheDocument();
  });

  it('an installed pack prefills the form with its values and offers Update', async () => {
    vi.mocked(api.inspectPack).mockResolvedValue({
      ...inspection, installedVersion: '0.9.0', installedValues: { SENDER: 'OLD', TARGET: 'T', PORT: '1' },
    });
    renderPage();
    await userEvent.upload(screen.getByTestId('pack-file'), zip());
    expect(await screen.findByLabelText(/SENDER/)).toHaveValue('OLD');
    expect(screen.getByLabelText(/PORT/)).toHaveValue('1');
    expect(screen.getByRole('button', { name: 'Update' })).toBeInTheDocument();
  });

  it('a refused import shows the server message', async () => {
    vi.mocked(api.importPack).mockRejectedValue({ status: 409, message: 'pack demo has running executions: e1' });
    renderPage();
    await userEvent.upload(screen.getByTestId('pack-file'), zip());
    await userEvent.type(await screen.findByLabelText(/SENDER/), 'S');
    await userEvent.type(screen.getByLabelText(/TARGET/), 'T');
    await userEvent.click(screen.getByRole('button', { name: 'Install' }));
    expect(await screen.findByText(/running executions: e1/)).toBeInTheDocument();
  });

  it('lists installed packs and deletes one after confirmation', async () => {
    vi.mocked(api.getPacks).mockResolvedValue([installed]);
    vi.mocked(api.deletePack).mockResolvedValue();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    expect(await screen.findByText('Demo pack')).toBeInTheDocument();
    expect(screen.getByText(/2 scenarios · 1 data sets · 1 sessions · 1 dictionaries/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(api.deletePack).toHaveBeenCalledWith('demo'));
  });
});
