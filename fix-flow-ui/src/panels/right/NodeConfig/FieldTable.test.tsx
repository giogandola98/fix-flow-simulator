import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { FieldTable } from './FieldTable';

describe('FieldTable', () => {
  it('renders a row per field with its resolved tag name', () => {
    render(<FieldTable fields={[{ tag: 55, value: 'EUR/USD' }, { tag: 167, value: 'FXSPOT' }]}
                       onChange={() => {}} idPrefix="t" />);
    expect(screen.getByText('Symbol')).toBeInTheDocument();
    expect(screen.getByText('SecurityType')).toBeInTheDocument();
    expect(screen.getByDisplayValue('EUR/USD')).toBeInTheDocument();
  });

  it('emits the whole array when a value changes', async () => {
    const onChange = vi.fn();
    render(<FieldTable fields={[{ tag: 55, value: 'EUR' }]} onChange={onChange} idPrefix="t" />);
    await userEvent.type(screen.getByDisplayValue('EUR'), '/USD');
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls.at(-1)![0][0].tag).toBe(55);
  });

  it('adds and removes rows', async () => {
    const onChange = vi.fn();
    render(<FieldTable fields={[{ tag: 55, value: 'EUR/USD' }]} onChange={onChange} idPrefix="t" />);

    await userEvent.click(screen.getByTestId('t-add-field'));
    expect(onChange).toHaveBeenLastCalledWith([{ tag: 55, value: 'EUR/USD' }, { tag: 0, value: '' }]);

    await userEvent.click(screen.getByTestId('t-remove-0'));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it('flags engine-managed session tags', () => {
    render(<FieldTable fields={[{ tag: 52, value: 'x' }]} onChange={() => {}} idPrefix="t" />);
    expect(screen.getByText('engine-managed')).toBeInTheDocument();
  });

  it('does not render its own datalist, even with several tables on screen', () => {
    const { container } = render(
      <>
        <FieldTable fields={[{ tag: 55, value: 'EUR/USD' }]} onChange={() => {}} idPrefix="a" />
        <FieldTable fields={[{ tag: 55, value: 'GBP/USD' }]} onChange={() => {}} idPrefix="b" />
      </>
    );
    expect(container.querySelectorAll('datalist').length).toBe(0);
  });
});
