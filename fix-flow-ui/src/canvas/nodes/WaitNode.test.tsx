import { render, screen } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import { describe, expect, it } from 'vitest';
import { WaitNode, describeDuration } from './WaitNode';
import { TimeoutConfig } from '../../types';

const timeout = (value: number, unit: TimeoutConfig['unit']): TimeoutConfig =>
  ({ value, unit, onTimeout: 'FAIL' });

function renderNode(type: string, config: Record<string, unknown>, t?: TimeoutConfig) {
  const props = {
    id: 'w1',
    type,
    data: { label: 'Delay', config, timeout: t, status: 'idle' },
    selected: false,
  } as never;
  return render(
    <ReactFlowProvider>
      <WaitNode {...props} />
    </ReactFlowProvider>,
  );
}

describe('describeDuration', () => {
  it('reads a WAIT duration from the timeout, where the editor writes it', () => {
    expect(describeDuration('WAIT', {}, timeout(2, 'SECONDS'))).toBe('2 SECONDS');
  });

  it('reads a TIMEOUT block the same way', () => {
    expect(describeDuration('TIMEOUT', {}, timeout(30, 'MINUTES'))).toBe('30 MINUTES');
  });

  it('reads a DELAY duration from config.delayMs, which is what the engine reads', () => {
    expect(describeDuration('DELAY', { delayMs: 250 }, undefined)).toBe('250 ms');
    // even when a timeout happens to be set, DELAY sleeps on delayMs
    expect(describeDuration('DELAY', { delayMs: 250 }, timeout(9, 'HOURS'))).toBe('250 ms');
  });

  it('still understands the inline shape a hand-written scenario may use', () => {
    expect(describeDuration('WAIT', { value: 5, unit: 'SECONDS' }, undefined)).toBe('5 SECONDS');
  });

  it('shows a dash, not a question mark, when nothing is configured', () => {
    expect(describeDuration('WAIT', {}, undefined)).toBe('—');
    expect(describeDuration('DELAY', {}, undefined)).toBe('—');
    expect(describeDuration('WAIT', undefined, undefined)).toBe('—');
  });

  it('does not treat a zero duration as unset', () => {
    expect(describeDuration('DELAY', { delayMs: 0 }, undefined)).toBe('0 ms');
    expect(describeDuration('WAIT', {}, timeout(0, 'SECONDS'))).toBe('0 SECONDS');
  });
});

describe('WaitNode', () => {
  it('shows the configured duration instead of a question mark', () => {
    renderNode('WAIT', {}, timeout(2, 'SECONDS'));
    expect(screen.getByText('2 SECONDS')).toBeInTheDocument();
    expect(screen.queryByText('?')).toBeNull();
  });

  it('labels a DELAY block DELAY, not WAIT', () => {
    renderNode('DELAY', { delayMs: 500 });
    expect(screen.getByText('DELAY')).toBeInTheDocument();
    expect(screen.getByText('500 ms')).toBeInTheDocument();
  });
});
