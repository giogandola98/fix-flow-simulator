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

  it('posts fail with the custom label', async () => {
    useExecutionStore.setState({
      operatorPrompt: { executionId: 'exec-1', nodeId: 'op', instruction: 'x', failLabel: 'Rejected' },
    });
    render(<OperatorPrompt />);
    await userEvent.click(screen.getByRole('button', { name: 'Rejected' }));
    expect(api.answerOperator).toHaveBeenCalledWith('exec-1', 'op', false, '');
  });
});
