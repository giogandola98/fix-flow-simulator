import { render } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import { describe, expect, it } from 'vitest';
import { DecisionNode } from './DecisionNode';

function renderNode(config: Record<string, unknown>) {
  const props = { id: 'd1', data: { label: 'Check status', config, status: 'idle' }, selected: false } as never;
  return render(
    <ReactFlowProvider>
      <DecisionNode {...props} />
    </ReactFlowProvider>,
  );
}

const handleIds = (container: HTMLElement) =>
  [...container.querySelectorAll('.react-flow__handle-bottom, .react-flow__handle-right')]
    .map((h) => h.getAttribute('data-handleid'));

describe('DecisionNode handles', () => {
  it('keeps the success/failure pair when the node has no branches', () => {
    const { container } = renderNode({ condition: '39 == 2' });
    expect(handleIds(container)).toEqual(['success', 'failure']);
  });

  it('exposes one handle per branch instead', () => {
    const { container } = renderNode({
      branches: [
        { branchId: 'b1', label: 'Filled', conditions: ['39 == 2'], targetNodeId: 'n1' },
        { branchId: 'b2', label: 'Rejected', conditions: ['39 == 8'], targetNodeId: 'n2' },
        { branchId: 'b3', label: 'Other', conditions: [], targetNodeId: 'n3' },
      ],
    });
    expect(handleIds(container)).toEqual(['b1', 'b2', 'b3']);
  });

  it('labels the branch handles', () => {
    const { getByText } = renderNode({
      branches: [{ branchId: 'b1', label: 'Filled', conditions: [], targetNodeId: '' }],
    });
    expect(getByText('Filled')).toBeInTheDocument();
  });

  it('shows the condition only while it is the single-condition form', () => {
    const { queryByText, rerender } = renderNode({ condition: '39 == 2' });
    expect(queryByText('39 == 2')).toBeInTheDocument();

    rerender(
      <ReactFlowProvider>
        <DecisionNode
          {...({
            id: 'd1',
            data: {
              label: 'Check status',
              config: { condition: '39 == 2', branches: [{ branchId: 'b1', label: 'Filled', conditions: [], targetNodeId: '' }] },
              status: 'idle',
            },
            selected: false,
          } as never)}
        />
      </ReactFlowProvider>,
    );
    expect(queryByText('39 == 2')).toBeNull();
    expect(queryByText('1 branches')).toBeInTheDocument();
  });
});
