import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '../i18n';
import OperatorPrompt from './OperatorPrompt';
import { useExecutionStore } from '../store/executionStore';
import * as api from '../api/executions';

vi.mock('../api/executions');

describe('OperatorPrompt', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(api.answerOperator).mockResolvedValue(undefined);
    vi.mocked(api.getOperatorPrompt).mockResolvedValue(null);
    useExecutionStore.setState({ activeExecutionId: 'exec-1', operatorPrompt: null });
  });

  it('renders nothing without an open prompt', () => {
    const { container } = render(<OperatorPrompt />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the instruction and posts confirm with the note', async () => {
    useExecutionStore.setState({
      operatorPrompt: { executionId: 'child-1', nodeId: 'op', instruction: 'Send the RFQ on the venue' },
    });
    render(<OperatorPrompt />);
    expect(screen.getByText('Send the RFQ on the venue')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Note (optional)'), 'sent');
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(api.answerOperator).toHaveBeenCalledWith('child-1', 'op', true, 'sent');
    await vi.waitFor(() => expect(useExecutionStore.getState().operatorPrompt).toBeNull());
  });

  it('keeps the next sibling prompt that arrived before the answer response', async () => {
    const next = { executionId: 'child-2', nodeId: 'op', instruction: 'second' };
    vi.mocked(api.getOperatorPrompt).mockResolvedValue(next);
    useExecutionStore.setState({
      operatorPrompt: { executionId: 'child-1', nodeId: 'op', instruction: 'first' },
    });
    render(<OperatorPrompt />);
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    await vi.waitFor(() => expect(useExecutionStore.getState().operatorPrompt).toEqual(next));
  });

  it('drops a stale read that resolves after a newer one', async () => {
    let resolveOld: (v: api.OperatorPromptData | null) => void = () => {};
    vi.mocked(api.getOperatorPrompt)
      .mockReturnValueOnce(new Promise((r) => { resolveOld = r; }))
      .mockResolvedValueOnce({ executionId: 'c2', nodeId: 'op', instruction: 'new' });
    const store = useExecutionStore.getState();
    const first = store.refreshOperatorPrompt('exec-1');
    await store.refreshOperatorPrompt('exec-1');
    resolveOld({ executionId: 'c1', nodeId: 'op', instruction: 'old' });
    await first;
    expect(useExecutionStore.getState().operatorPrompt?.instruction).toBe('new');
  });

  it('posts fail with the custom label', async () => {
    useExecutionStore.setState({
      operatorPrompt: { executionId: 'exec-1', nodeId: 'op', instruction: 'x', failLabel: 'Rejected' },
    });
    render(<OperatorPrompt />);
    await userEvent.click(screen.getByRole('button', { name: 'Rejected' }));
    expect(api.answerOperator).toHaveBeenCalledWith('exec-1', 'op', false, '');
  });
});
