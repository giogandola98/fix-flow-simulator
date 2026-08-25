import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ReactFlowProvider } from '@xyflow/react';
import { ValidateNode } from './ValidateNode';

const props = {
  id: 'v1',
  data: { label: 'Check fields', config: { rules: [{ tag: 35 }] }, status: 'idle' },
  selected: false,
} as never;

describe('ValidateNode handles', () => {
  it('exposes a failure source handle and keeps the success one anonymous', () => {
    const { container } = render(
      <ReactFlowProvider>
        <ValidateNode {...props} />
      </ReactFlowProvider>,
    );
    const sources = container.querySelectorAll('.react-flow__handle-bottom, .react-flow__handle-right');
    expect(sources.length).toBe(2);
    // the failure handle is addressable...
    expect(container.querySelector('[data-handleid="failure"]')).toBeTruthy();
    // ...while the success handle stays id-less, so edges saved with sourceHandle: null still bind
    expect(container.querySelector('.react-flow__handle-bottom')?.getAttribute('data-handleid')).toBeFalsy();
  });
});
