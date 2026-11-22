import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../../i18n';
import DataSetPage from './DataSetPage';
import { useDataSetStore } from '../../store/dataSetStore';
import * as api from '../../api/datasets';

vi.mock('../../api/datasets');

const imported: api.DataSet = {
  id: 'ds1', name: 'rates', columns: ['ccy', 'rate'], rows: [['EUR', '1.1'], ['GBP', '0.85']], rowCount: 2,
};

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DataSetPage />
    </QueryClientProvider>,
  );

describe('DataSetPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    useDataSetStore.setState({ isOpen: true, selectedId: null });
    vi.mocked(api.getDataSets).mockResolvedValue([]);
    vi.mocked(api.getDataSet).mockResolvedValue(imported);
    vi.mocked(api.importDataSet).mockResolvedValue(imported);
    vi.mocked(api.dataSetExportUrl).mockReturnValue('/x');
  });

  it('importing a CSV file shows its rows', async () => {
    renderPage();
    const file = new File(['ccy,rate\nEUR,1.1\nGBP,0.85\n'], 'rates.csv', { type: 'text/csv' });
    await userEvent.upload(screen.getByTestId('dataset-file'), file);

    expect(api.importDataSet).toHaveBeenCalledWith(file, 'rates');
    expect(await screen.findByDisplayValue('GBP')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1.1')).toBeInTheDocument();
    expect(screen.getByDisplayValue('ccy')).toBeInTheDocument();
  });

  it('editing a cell and saving calls PUT with the edited rows', async () => {
    useDataSetStore.setState({ selectedId: 'ds1' });
    vi.mocked(api.updateDataSet).mockResolvedValue(imported);
    renderPage();
    const cellInput = await screen.findByLabelText('r1c2');
    await userEvent.clear(cellInput);
    await userEvent.type(cellInput, '1.2');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.updateDataSet).toHaveBeenCalledWith('ds1', {
      name: 'rates', columns: ['ccy', 'rate'], rows: [['EUR', '1.2'], ['GBP', '0.85']],
    }));
  });
});
