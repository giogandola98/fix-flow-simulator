import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useDynamicHandles } from './useDynamicHandles';

const updateNodeInternals = vi.fn();

vi.mock('@xyflow/react', () => ({
  useUpdateNodeInternals: () => updateNodeInternals,
}));

function Probe({ nodeId, ids, unrelated }: { nodeId: string; ids: string[]; unrelated?: string }) {
  useDynamicHandles(nodeId, ids);
  return <div>{unrelated}</div>;
}

describe('useDynamicHandles', () => {
  beforeEach(() => updateNodeInternals.mockReset());

  it('re-measures the node on mount, so handles rendered right away are registered', () => {
    render(<Probe nodeId="d1" ids={['b1']} />);
    expect(updateNodeInternals).toHaveBeenCalledWith('d1');
  });

  it('re-measures when a handle is added', () => {
    const { rerender } = render(<Probe nodeId="d1" ids={['b1']} />);
    updateNodeInternals.mockReset();

    rerender(<Probe nodeId="d1" ids={['b1', 'b2']} />);

    expect(updateNodeInternals).toHaveBeenCalledWith('d1');
  });

  it('re-measures when a handle is removed', () => {
    const { rerender } = render(<Probe nodeId="d1" ids={['b1', 'b2']} />);
    updateNodeInternals.mockReset();

    rerender(<Probe nodeId="d1" ids={['b1']} />);

    expect(updateNodeInternals).toHaveBeenCalledTimes(1);
  });

  it('does not re-measure while something unrelated changes', () => {
    // node data is rebuilt on every store change, so this fires on every keystroke in a label
    const { rerender } = render(<Probe nodeId="d1" ids={['b1']} unrelated="Fil" />);
    updateNodeInternals.mockReset();

    rerender(<Probe nodeId="d1" ids={['b1']} unrelated="Fill" />);
    rerender(<Probe nodeId="d1" ids={['b1']} unrelated="Fille" />);
    rerender(<Probe nodeId="d1" ids={[...['b1']]} unrelated="Filled" />);

    expect(updateNodeInternals).not.toHaveBeenCalled();
  });

  it('re-measures when the node itself changes', () => {
    const { rerender } = render(<Probe nodeId="d1" ids={['b1']} />);
    updateNodeInternals.mockReset();

    rerender(<Probe nodeId="d2" ids={['b1']} />);

    expect(updateNodeInternals).toHaveBeenCalledWith('d2');
  });

  it('handles a node with no dynamic handles at all', () => {
    render(<Probe nodeId="d1" ids={[]} />);
    expect(updateNodeInternals).toHaveBeenCalledWith('d1');
  });
});
