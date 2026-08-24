import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import '../../../i18n';
import { GroupEditor, GroupSpec } from './GroupEditor';

const twoLegs: GroupSpec[] = [{
  counterTag: 555,
  entries: [
    { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '1' }] },
    { fields: [{ tag: 600, value: 'EUR/USD' }, { tag: 624, value: '2' }] },
  ],
}];

describe('GroupEditor', () => {
  it('heads each group with its counter tag, name and entry count', () => {
    render(<GroupEditor groups={twoLegs} onChange={() => {}} idPrefix="g" />);
    expect(screen.getByText(/555/)).toBeInTheDocument();
    expect(screen.getByText(/NoLegs/)).toBeInTheDocument();
    expect(screen.getByText(/2 entries/)).toBeInTheDocument();
  });

  it('derives the counter from entry count and never lets it be typed', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    const counter = screen.getByTestId('g-counter-0') as HTMLInputElement;
    expect(counter.value).toBe('2');
    expect(counter.readOnly).toBe(true);

    await userEvent.click(screen.getByTestId('g-add-entry-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries).toHaveLength(3);
  });

  it('adds a group with one empty entry', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={[]} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-add-group'));
    await userEvent.type(screen.getByTestId('g-new-counter'), '864');
    await userEvent.click(screen.getByTestId('g-confirm-group'));
    expect(onChange).toHaveBeenCalledWith([{ counterTag: 864, entries: [{ fields: [] }] }]);
  });

  it('edits a field inside an entry', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.clear(screen.getByTestId('g-0-1-value-1'));
    await userEvent.type(screen.getByTestId('g-0-1-value-1'), '2');
    expect(onChange).toHaveBeenCalled();
  });

  it('duplicates, deletes and reorders entries', async () => {
    const onChange = vi.fn();
    const { rerender } = render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);

    await userEvent.click(screen.getByTestId('g-dup-entry-0-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries).toHaveLength(3);

    rerender(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-del-entry-0-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries).toHaveLength(1);

    rerender(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-down-entry-0-0'));
    expect(onChange.mock.calls.at(-1)![0][0].entries[0].fields[1].value).toBe('2');
  });

  it('removes a whole group', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-del-group-0'));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it('nests a sub-group inside an entry', async () => {
    const onChange = vi.fn();
    render(<GroupEditor groups={twoLegs} onChange={onChange} idPrefix="g" />);
    await userEvent.click(screen.getByTestId('g-add-subgroup-0-0'));
    await userEvent.type(screen.getByTestId('g-0-0-sub-new-counter'), '864');
    await userEvent.click(screen.getByTestId('g-0-0-sub-confirm-group'));
    expect(onChange.mock.calls.at(-1)![0][0].entries[0].groups).toEqual(
      [{ counterTag: 864, entries: [{ fields: [] }] }]);
  });

  it('stops offering sub-groups past depth 3', () => {
    render(<GroupEditor groups={twoLegs} onChange={() => {}} idPrefix="g" depth={3} />);
    expect(screen.queryByTestId('g-add-subgroup-0-0')).toBeNull();
    expect(screen.getByText(/nesting limit/i)).toBeInTheDocument();
  });

  it('shows delimiter helper text naming the expected first field', () => {
    render(<GroupEditor groups={twoLegs} onChange={() => {}} idPrefix="g" />);
    // NoLegs (555) delimiter is 600 LegSymbol; twoLegs has two entries, so expect >= 1 match
    expect(screen.getAllByText(/delimiter tag/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/600 LegSymbol/).length).toBeGreaterThan(0);
  });

  it('warns when an entry\'s first field is not the known delimiter', () => {
    const wrongOrder: GroupSpec[] = [{
      counterTag: 555,
      entries: [
        { fields: [{ tag: 624, value: '1' }, { tag: 600, value: 'EUR/USD' }] },
      ],
    }];
    render(<GroupEditor groups={wrongOrder} onChange={() => {}} idPrefix="g" />);
    expect(screen.getByTestId('g-delimiter-warning-0-0')).toBeInTheDocument();
  });

  it('does not warn when the first field already matches the delimiter', () => {
    render(<GroupEditor groups={twoLegs} onChange={() => {}} idPrefix="g" />);
    expect(screen.queryByTestId('g-delimiter-warning-0-0')).toBeNull();
  });

  it('stays silent about the delimiter for a counter tag outside GROUP_DELIMITERS', () => {
    const unknownCounter: GroupSpec[] = [{
      counterTag: 9999,
      entries: [{ fields: [{ tag: 1, value: 'x' }] }],
    }];
    render(<GroupEditor groups={unknownCounter} onChange={() => {}} idPrefix="g" />);
    expect(screen.queryByText(/delimiter tag/i)).toBeNull();
    expect(screen.queryByTestId('g-delimiter-warning-0-0')).toBeNull();
  });
});
