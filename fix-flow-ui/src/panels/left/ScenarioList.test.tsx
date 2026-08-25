import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ScenarioList } from './ScenarioList';
import * as api from '../../api/scenarios';

vi.mock('../../api/scenarios');

const scenario = {
  id: 's-1',
  name: 'NOrder - MKT - Accept',
  description: '',
  version: '1',
  sessionRef: 'default',
  nodeCount: 5,
};

function renderList() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ScenarioList />
    </QueryClientProvider>,
  );
}

describe('ScenarioList duplicate', () => {
  beforeEach(() => {
    vi.mocked(api.getScenarios).mockResolvedValue([scenario] as never);
    vi.mocked(api.duplicateScenario).mockResolvedValue({
      ...scenario,
      id: 's-2',
      name: 'NOrder - MKT - Accept (copy)',
    } as never);
  });

  it('offers a duplicate action per scenario', async () => {
    renderList();
    await screen.findByText('NOrder - MKT - Accept');
    expect(screen.getByText('⧉')).toBeInTheDocument();
  });

  it('duplicates the scenario it belongs to and refreshes the list', async () => {
    renderList();
    await screen.findByText('NOrder - MKT - Accept');

    await userEvent.click(screen.getByText('⧉'));

    await waitFor(() => expect(api.duplicateScenario).toHaveBeenCalledWith('s-1'));
    // the query is invalidated, so the list re-fetches and the copy shows up without a reload
    await waitFor(() =>
      expect(vi.mocked(api.getScenarios).mock.calls.length).toBeGreaterThan(1),
    );
  });

  it('does not open the scenario when duplicating it', async () => {
    renderList();
    await screen.findByText('NOrder - MKT - Accept');

    await userEvent.click(screen.getByText('⧉'));

    await waitFor(() => expect(api.duplicateScenario).toHaveBeenCalled());
    expect(api.getScenario).not.toHaveBeenCalled();
  });
});
