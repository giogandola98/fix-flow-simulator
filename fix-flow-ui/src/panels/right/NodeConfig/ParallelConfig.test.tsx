import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { ParallelConfig } from './ParallelConfig';
import { useScenarioStore } from '../../../store/scenarioStore';
import { Scenario, ScenarioNode } from '../../../types';

const par: ScenarioNode = {
  id: 'p1', name: 'Inquiries', type: 'PARALLEL', config: {}, position: { x: 0, y: 0 },
};
const scenario = (id: string, name: string): Scenario => ({
  id, name, description: '', version: '1', sessionRef: '', nodeCount: 3,
});

const cfg = () => useScenarioStore.getState().nodes[0].config as {
  targetScenarioId?: string; rows?: 'all' | number[]; maxConcurrency?: number;
};
const live = () => useScenarioStore.getState().nodes[0];

describe('ParallelConfig', () => {
  beforeEach(() => {
    useScenarioStore.setState({
      nodes: [par], edges: [],
      scenarios: [scenario('parent', 'Parent'), scenario('child', 'RFQ inquiry')],
      activeScenario: scenario('parent', 'Parent'),
    });
  });

  it('offers the other scenarios as target and stores the choice', async () => {
    render(<ParallelConfig node={par} />);
    const select = screen.getByTestId('parallel-target');
    expect(screen.queryByRole('option', { name: 'Parent' })).toBeNull();
    await userEvent.selectOptions(select, 'child');
    expect(cfg().targetScenarioId).toBe('child');
  });

  it('defaults to all rows and stores a list of row indexes', async () => {
    const { rerender } = render(<ParallelConfig node={par} />);
    expect((screen.getByTestId('parallel-rows-mode') as HTMLSelectElement).value).toBe('all');
    expect(screen.queryByTestId('parallel-rows-list')).toBeNull();

    await userEvent.selectOptions(screen.getByTestId('parallel-rows-mode'), 'list');
    rerender(<ParallelConfig node={live()} />);
    await userEvent.type(screen.getByTestId('parallel-rows-list'), '0, 2,5');
    expect(cfg().rows).toEqual([0, 2, 5]);

    await userEvent.selectOptions(screen.getByTestId('parallel-rows-mode'), 'all');
    expect(cfg().rows).toBe('all');
  });

  it('stores max concurrency as a number and clears it back to the default', async () => {
    render(<ParallelConfig node={par} />);
    const input = screen.getByTestId('parallel-max-concurrency');
    expect(input).toHaveAttribute('placeholder', '10');
    await userEvent.type(input, '4');
    expect(cfg().maxConcurrency).toBe(4);
    await userEvent.clear(input);
    expect(cfg().maxConcurrency).toBeUndefined();
  });

  it('edits input variables', async () => {
    render(<ParallelConfig node={par} />);
    await userEvent.click(screen.getByTestId('parallel-add-input-var'));
    expect((cfg() as { inputVars?: unknown[] }).inputVars).toEqual([{ from: '', to: '' }]);
  });
});
