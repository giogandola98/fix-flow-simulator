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

describe('ScenarioList layout', () => {
  beforeEach(() => {
    vi.mocked(api.getScenarios).mockResolvedValue(
      Array.from({ length: 40 }, (_, i) => ({ ...scenario, id: `s-${i}`, name: `Bulk ${i}` })) as never,
    );
  });

  // jsdom has no layout, so this pins the contract that makes the column scrollable rather than
  // the scrolling itself: a bounded column, with the list as the part that gives way (issue #100).
  it('is a full-height column so the list can overflow inside it', async () => {
    const { container } = renderList();
    await screen.findByText('Bulk 0');

    const root = container.firstElementChild as HTMLElement;
    expect(root.className).toContain('h-full');
    expect(root.className).toContain('flex-col');
  });

  it('gives the list itself the scrollbar', async () => {
    renderList();
    await screen.findByText('Bulk 0');

    const list = screen.getByTestId('scenario-list');
    expect(list.className).toContain('overflow-y-auto');
    // min-h-0 is what lets a flex child shrink below its content height
    expect(list.className).toContain('min-h-0');
    expect(list.className).toContain('flex-1');
  });

  it('keeps every scenario in the list, however many there are', async () => {
    renderList();
    await screen.findByText('Bulk 0');
    expect(screen.getByText('Bulk 39')).toBeInTheDocument();
  });
});
