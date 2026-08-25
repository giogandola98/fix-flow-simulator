import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReactFlowProvider } from '@xyflow/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CanvasToolbar } from './CanvasToolbar';
import { useScenarioStore } from '../store/scenarioStore';
import * as pdf from '../lib/exportFlowchartPdf';

vi.mock('../lib/exportFlowchartPdf', () => ({ exportFlowchartToPdf: vi.fn() }));

const node = {
  id: 'n1', name: 'Start', type: 'START' as const, config: {}, position: { x: 0, y: 0 },
};

function renderToolbar() {
  // the real canvas markup the export reads from
  const viewport = document.createElement('div');
  viewport.className = 'react-flow__viewport';
  document.body.appendChild(viewport);
  return render(
    <ReactFlowProvider>
      <CanvasToolbar />
    </ReactFlowProvider>,
  );
}

describe('CanvasToolbar PDF export', () => {
  beforeEach(() => {
    vi.mocked(pdf.exportFlowchartToPdf).mockReset();
    vi.mocked(pdf.exportFlowchartToPdf).mockResolvedValue('Flow.pdf');
    useScenarioStore.setState({
      nodes: [node],
      edges: [],
      activeScenario: { id: 's1', name: 'Flow', description: '', version: '1', sessionRef: 'd', nodeCount: 1 } as never,
    });
  });

  it('exports the flowchart with the active scenario name', async () => {
    renderToolbar();
    await userEvent.click(screen.getByTitle('canvas.exportPdfTitle'));

    await waitFor(() => expect(pdf.exportFlowchartToPdf).toHaveBeenCalledTimes(1));
    const args = vi.mocked(pdf.exportFlowchartToPdf).mock.calls[0][0];
    expect(args.scenarioName).toBe('Flow');
    expect((args.viewport as HTMLElement).className).toContain('react-flow__viewport');
  });

  it('is disabled while the canvas is empty', () => {
    useScenarioStore.setState({ nodes: [], edges: [] });
    renderToolbar();
    expect(screen.getByTitle('canvas.exportPdfTitle')).toBeDisabled();
  });

  it('surfaces a failure instead of silently doing nothing', async () => {
    vi.mocked(pdf.exportFlowchartToPdf).mockRejectedValue(new Error('render failed'));
    renderToolbar();

    await userEvent.click(screen.getByTitle('canvas.exportPdfTitle'));

    expect(await screen.findByRole('alert')).toHaveTextContent('render failed');
    // and the button is usable again for a retry
    expect(screen.getByTitle('canvas.exportPdfTitle')).toBeEnabled();
  });
});
