import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../i18n';
import RunDialog from './RunDialog';
import * as api from '../api/datasets';

vi.mock('../api/datasets');

const rates: api.DataSet = {
  id: 'ds1', name: 'rates', columns: ['ccy', 'rate', 'a', 'b'], rowCount: 2,
  rows: [['EUR', '1.1', 'x', 'y'], ['GBP', '0.85', 'x', 'y']],
};
const other: api.DataSet = { id: 'ds2', name: 'other', columns: ['c'], rows: [['1']], rowCount: 1 };

const renderDialog = (props: { defaultDatasetRef?: string } = {}) => {
  const onRun = vi.fn();
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RunDialog onRun={onRun} onClose={onClose} {...props} />
    </QueryClientProvider>,
  );
  return { onRun, onClose };
};

describe('RunDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(api.getDataSets).mockResolvedValue([rates, other]);
    vi.mocked(api.getDataSet).mockImplementation(async (id: string) => (id === 'ds1' ? rates : other));
  });

  it('picks a data set and a row', async () => {
    const { onRun } = renderDialog();
    await screen.findByRole('option', { name: 'rates' });
    await userEvent.selectOptions(screen.getByLabelText('Data set'), 'ds1');
    await userEvent.selectOptions(await screen.findByLabelText('Row'), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Start' }));
    expect(onRun).toHaveBeenCalledWith({ datasetId: 'ds1', rowIndex: 1 });
  });

  it('runs all rows', async () => {
    const { onRun } = renderDialog();
    await screen.findByRole('option', { name: 'rates' });
    await userEvent.selectOptions(screen.getByLabelText('Data set'), 'ds1');
    await userEvent.click(await screen.findByLabelText('All rows'));
    await userEvent.click(screen.getByRole('button', { name: 'Start' }));
    expect(onRun).toHaveBeenCalledWith({ datasetId: 'ds1', allRows: true });
  });

  it('defaults to the scenario data set by name, row 0', async () => {
    const { onRun } = renderDialog({ defaultDatasetRef: 'rates' });
    expect(await screen.findByDisplayValue('rates')).toBeInTheDocument();
    await screen.findByLabelText('Row');
    await userEvent.click(screen.getByRole('button', { name: 'Start' }));
    expect(onRun).toHaveBeenCalledWith({ datasetId: 'ds1', rowIndex: 0 });
  });

  it('sends key/value variables and no data set when none is picked', async () => {
    const { onRun } = renderDialog();
    await screen.findByLabelText('Data set');
    await userEvent.click(screen.getByRole('button', { name: '+ Variable' }));
    await userEvent.type(screen.getByLabelText('Variable name'), 'k');
    await userEvent.type(screen.getByLabelText('Variable value'), 'v');
    await userEvent.click(screen.getByRole('button', { name: 'Start' }));
    expect(onRun).toHaveBeenCalledWith({ vars: { k: 'v' } });
  });
});
