import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, beforeEach } from 'vitest';
import { ExpectFIXConfig } from './ExpectFIXConfig';
import { useScenarioStore } from '../../../store/scenarioStore';

const node = {
  id: 'e1', name: 'NEW ORDER SINGLE', type: 'EXPECT_FIX' as const,
  config: {}, position: { x: 0, y: 0 },
};

const cfgOf = () => useScenarioStore.getState().nodes[0].config as Record<string, unknown>;

describe('ExpectFIXConfig correlation', () => {
  beforeEach(() => {
    useScenarioStore.setState({ nodes: [node], edges: [] });
  });

  it('does not write a correlation block when only a MsgType is set', async () => {
    render(<ExpectFIXConfig node={node} />);
    const [sourceTag] = screen.getAllByPlaceholderText('e.g. 11');
    await userEvent.type(sourceTag, '11');
    await userEvent.clear(sourceTag);

    // an empty correlation block made the engine wait on tag 11 == '' forever (issue #77)
    expect(cfgOf()).not.toHaveProperty('correlation');
  });

  it('keeps every digit typed into a correlation tag', async () => {
    render(<ExpectFIXConfig node={node} />);
    const [sourceTag] = screen.getAllByPlaceholderText('e.g. 11');
    await userEvent.type(sourceTag, '11');
    expect(cfgOf().correlation).toEqual({ sourceTag: 11 });
  });

  it('removes the block again once its last field is cleared', async () => {
    useScenarioStore.setState({
      nodes: [{ ...node, config: { msgType: 'D', correlation: { sourceTag: 11 } } }],
      edges: [],
    });
    render(<ExpectFIXConfig node={useScenarioStore.getState().nodes[0]} />);
    const [sourceTag] = screen.getAllByPlaceholderText('e.g. 11');
    await userEvent.clear(sourceTag);

    expect(cfgOf()).not.toHaveProperty('correlation');
    expect(cfgOf().msgType).toBe('D');
  });
});
