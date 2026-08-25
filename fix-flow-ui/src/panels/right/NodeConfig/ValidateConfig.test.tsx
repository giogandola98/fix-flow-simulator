import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, beforeEach } from 'vitest';
import { ValidateConfig } from './ValidateConfig';
import { useScenarioStore } from '../../../store/scenarioStore';

const node = {
  id: 'v1', name: 'Validate', type: 'VALIDATE' as const,
  config: { rules: [{ tag: 609, rule: 'EQUALS', value: 'FXSPOT' }] },
  position: { x: 0, y: 0 },
};

describe('ValidateConfig group inputs', () => {
  beforeEach(() => {
    useScenarioStore.setState({ nodes: [node], edges: [] });
  });

  it('renders a group tag and index input per rule', () => {
    render(<ValidateConfig node={node} />);
    expect(screen.getByTestId('validate-grouptag-0')).toBeInTheDocument();
    expect(screen.getByTestId('validate-index-0')).toBeInTheDocument();
  });

  it('writes groupTag and index into the rule', async () => {
    render(<ValidateConfig node={node} />);
    await userEvent.type(screen.getByTestId('validate-grouptag-0'), '555');
    await userEvent.type(screen.getByTestId('validate-index-0'), '1');

    const rules = (useScenarioStore.getState().nodes[0].config as
      { rules: Array<Record<string, unknown>> }).rules;
    expect(rules[0].groupTag).toBe(555);
    expect(rules[0].index).toBe('1');
  });

  it('omits both keys when the inputs are cleared', async () => {
    useScenarioStore.setState({
      nodes: [{ ...node, config: { rules: [{ tag: 609, rule: 'EQUALS', value: 'FXSPOT', groupTag: 555, index: '0' }] } }],
      edges: [],
    });
    render(<ValidateConfig node={useScenarioStore.getState().nodes[0]} />);
    await userEvent.clear(screen.getByTestId('validate-grouptag-0'));

    const rules = (useScenarioStore.getState().nodes[0].config as
      { rules: Array<Record<string, unknown>> }).rules;
    expect(rules[0]).not.toHaveProperty('groupTag');
  });

  it('accepts the wildcard index', async () => {
    render(<ValidateConfig node={node} />);
    await userEvent.type(screen.getByTestId('validate-index-0'), '*');
    const rules = (useScenarioStore.getState().nodes[0].config as
      { rules: Array<Record<string, unknown>> }).rules;
    expect(rules[0].index).toBe('*');
  });
});

describe('ValidateConfig source node', () => {
  const expectNode = {
    id: 'e1', name: 'NEW ORDER SINGLE', type: 'EXPECT_FIX' as const,
    config: { msgType: 'D' }, position: { x: 0, y: 0 },
  };

  beforeEach(() => {
    useScenarioStore.setState({ nodes: [expectNode, node], edges: [] });
  });

  it('defaults to the last received message and lists the receiving blocks', () => {
    render(<ValidateConfig node={node} />);
    const select = screen.getByTestId('validate-source-node') as HTMLSelectElement;
    expect(select.value).toBe('');
    expect(screen.getByRole('option', { name: 'NEW ORDER SINGLE' })).toBeInTheDocument();
  });

  it('writes the chosen source node into the config', async () => {
    render(<ValidateConfig node={node} />);
    await userEvent.selectOptions(screen.getByTestId('validate-source-node'), 'e1');
    const cfg = useScenarioStore.getState().nodes[1].config as { sourceNodeId?: string };
    expect(cfg.sourceNodeId).toBe('e1');
  });

  it('drops the key again when the default is re-selected', async () => {
    useScenarioStore.setState({
      nodes: [expectNode, { ...node, config: { ...node.config, sourceNodeId: 'e1' } }],
      edges: [],
    });
    render(<ValidateConfig node={useScenarioStore.getState().nodes[1]} />);
    await userEvent.selectOptions(screen.getByTestId('validate-source-node'), '');
    expect(useScenarioStore.getState().nodes[1].config).not.toHaveProperty('sourceNodeId');
  });
});
