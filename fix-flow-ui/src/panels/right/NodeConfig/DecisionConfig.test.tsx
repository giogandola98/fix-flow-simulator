import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { DecisionConfig } from './DecisionConfig';
import { useScenarioStore } from '../../../store/scenarioStore';
import { ScenarioNode } from '../../../types';

const decision: ScenarioNode = {
  id: 'd1', name: 'Check status', type: 'DECISION', config: {}, position: { x: 0, y: 0 },
};
const target: ScenarioNode = {
  id: 't1', name: 'Send confirm', type: 'SEND_FIX', config: {}, position: { x: 0, y: 100 },
};

interface Branch { branchId: string; label: string; conditions: string[]; targetNodeId: string }

const cfg = () => useScenarioStore.getState().nodes[0].config as { condition?: string; branches?: Branch[] };
const branches = () => cfg().branches ?? [];

describe('DecisionConfig branches', () => {
  beforeEach(() => {
    useScenarioStore.setState({ nodes: [decision, target], edges: [] });
  });

  it('starts on the single-condition form', () => {
    render(<DecisionConfig node={decision} />);
    expect(screen.getByTestId('decision-condition')).toBeInTheDocument();
  });

  it('adds a branch with one empty condition row', async () => {
    render(<DecisionConfig node={decision} />);
    await userEvent.click(screen.getByTestId('decision-add-branch'));

    expect(branches()).toHaveLength(1);
    expect(branches()[0].conditions).toEqual(['']);
    expect(screen.getByTestId('decision-condition-0-0')).toBeInTheDocument();
    // the single-condition input steps aside once branches exist
    expect(screen.queryByTestId('decision-condition')).toBeNull();
  });

  it('stores a label, several conditions and a target', async () => {
    useScenarioStore.setState({
      nodes: [{ ...decision, config: { branches: [{ branchId: 'b1', label: '', conditions: [''], targetNodeId: '' }] } }, target],
      edges: [],
    });
    render(<DecisionConfig node={useScenarioStore.getState().nodes[0]} />);

    await userEvent.type(screen.getByTestId('decision-branch-label-0'), 'Filled');
    await userEvent.type(screen.getByTestId('decision-condition-0-0'), '39 == 2');
    await userEvent.click(screen.getByTestId('decision-add-condition-0'));
    await userEvent.type(screen.getByTestId('decision-condition-0-1'), '151 == 0');
    await userEvent.selectOptions(screen.getByTestId('decision-target-0'), 't1');

    expect(branches()[0].label).toBe('Filled');
    expect(branches()[0].conditions).toEqual(['39 == 2', '151 == 0']);
    expect(branches()[0].targetNodeId).toBe('t1');
  });

  it('draws the branch edge on the canvas when a target is chosen', async () => {
    useScenarioStore.setState({
      nodes: [{ ...decision, config: { branches: [{ branchId: 'b1', label: 'Filled', conditions: ['a == a'], targetNodeId: '' }] } }, target],
      edges: [],
    });
    render(<DecisionConfig node={useScenarioStore.getState().nodes[0]} />);

    await userEvent.selectOptions(screen.getByTestId('decision-target-0'), 't1');

    expect(useScenarioStore.getState().edges).toEqual([
      { from: 'd1', to: 't1', label: 'Filled', sourceHandle: 'b1' },
    ]);
  });

  it('removes the branch and its edge together', async () => {
    useScenarioStore.setState({
      nodes: [{ ...decision, config: { branches: [{ branchId: 'b1', label: 'Filled', conditions: ['a == a'], targetNodeId: 't1' }] } }, target],
      edges: [{ from: 'd1', to: 't1', label: 'Filled', sourceHandle: 'b1' }],
    });
    render(<DecisionConfig node={useScenarioStore.getState().nodes[0]} />);

    await userEvent.click(screen.getByTestId('decision-remove-branch-0'));

    expect(branches()).toHaveLength(0);
    expect(useScenarioStore.getState().edges).toHaveLength(0);
  });

  it('marks a branch with no real condition as the default', () => {
    useScenarioStore.setState({
      nodes: [{ ...decision, config: { branches: [{ branchId: 'b1', label: 'Anything else', conditions: [''], targetNodeId: '' }] } }, target],
      edges: [],
    });
    render(<DecisionConfig node={useScenarioStore.getState().nodes[0]} />);
    expect(screen.getByText('nodeConfig.decision.defaultBranch')).toBeInTheDocument();
  });
});
