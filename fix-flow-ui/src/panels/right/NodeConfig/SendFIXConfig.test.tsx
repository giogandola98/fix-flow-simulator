import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '../../../i18n';
import { SendFIXConfig } from './SendFIXConfig';
import { ScenarioNode } from '../../../types';

const node: ScenarioNode = {
  id: 'n1',
  name: 'Send Order',
  type: 'SEND_FIX',
  config: {
    msgType: 'D',
    fields: [
      { tag: 55, value: 'AAPL' }, // known -> Symbol
      { tag: 54, value: '1' },    // known -> Side
      { tag: 8, value: 'FIX.4.4' }, // engine-managed
      { tag: 99999, value: 'x' }, // unknown
    ],
  },
};

describe('SendFIXConfig', () => {
  it('renders human-readable FIX tag names for known tags', () => {
    render(<SendFIXConfig node={node} />);
    expect(screen.getByText('Symbol')).toBeInTheDocument();
    expect(screen.getByText('Side')).toBeInTheDocument();
  });

  it('marks engine-managed tags', () => {
    render(<SendFIXConfig node={node} />);
    expect(screen.getByText('engine-managed')).toBeInTheDocument();
  });

  it('shows a placeholder for unknown tags', () => {
    render(<SendFIXConfig node={node} />);
    // unknown tag renders an em-dash placeholder cell
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  });
});
