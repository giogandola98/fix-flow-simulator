import { describe, expect, it, vi } from 'vitest';
import type { Node } from '@xyflow/react';
import {
  exportFlowchartToPdf,
  fitToPage,
  imageSizeFor,
  pdfFileName,
  GRAPH_PADDING,
  MAX_IMAGE_SIDE,
  PIXEL_RATIO,
  CANVAS_BACKGROUND,
} from './exportFlowchartPdf';

/** Measured nodes, as React Flow hands them over once it has sized them. */
const nodes = [
  { id: 'a', position: { x: 0, y: 0 }, data: {}, measured: { width: 160, height: 60 } },
  { id: 'b', position: { x: 200, y: 400 }, data: {}, measured: { width: 160, height: 60 } },
] as unknown as Node[];

function fakePdf() {
  const pdf = {
    orientation: '' as string,
    added: [] as unknown[],
    saved: null as string | null,
    internal: { pageSize: { getWidth: () => 297, getHeight: () => 210 } },
    addImage: (...args: unknown[]) => { pdf.added.push(args); },
    save: (name: string) => { pdf.saved = name; },
  };
  return pdf;
}

describe('fitToPage', () => {
  it('centres the image and keeps its aspect ratio', () => {
    const p = fitToPage(1000, 500, 297, 210, 10);
    expect(p.width / p.height).toBeCloseTo(2, 5);
    expect(p.x).toBeCloseTo((297 - p.width) / 2, 5);
    expect(p.y).toBeCloseTo((210 - p.height) / 2, 5);
  });

  it('never spills outside the printable area', () => {
    const p = fitToPage(4000, 100, 297, 210, 10);
    expect(p.width).toBeLessThanOrEqual(297 - 20 + 0.001);
    expect(p.height).toBeLessThanOrEqual(210 - 20 + 0.001);
    expect(p.x).toBeGreaterThanOrEqual(10 - 0.001);
  });

  it('fills the page for a tall image too', () => {
    const p = fitToPage(500, 1000, 210, 297, 10);
    expect(p.height).toBeCloseTo(297 - 20, 5);
  });
});

describe('imageSizeFor', () => {
  it('adds the padding around the graph', () => {
    expect(imageSizeFor(800, 600)).toEqual({
      width: 800 + GRAPH_PADDING * 2,
      height: 600 + GRAPH_PADDING * 2,
    });
  });

  it('keeps the aspect ratio of a tall graph instead of letterboxing it', () => {
    const { width, height } = imageSizeFor(300, 3000);
    expect(height).toBeGreaterThan(width);
    expect(width / height).toBeCloseTo((300 + GRAPH_PADDING * 2) / (3000 + GRAPH_PADDING * 2), 2);
  });

  it('caps the longest side for a very large graph', () => {
    const { width, height } = imageSizeFor(20000, 8000);
    expect(Math.max(width, height)).toBeLessThanOrEqual(MAX_IMAGE_SIDE);
    expect(width).toBeGreaterThan(height);
  });

  it('survives a single-node graph with zero-sized bounds', () => {
    const { width, height } = imageSizeFor(0, 0);
    expect(width).toBeGreaterThan(0);
    expect(height).toBeGreaterThan(0);
  });
});

describe('pdfFileName', () => {
  it('is named after the scenario', () => {
    expect(pdfFileName('NOrder - MKT - Accept')).toBe('NOrder - MKT - Accept.pdf');
  });

  it('strips characters a file name cannot carry', () => {
    expect(pdfFileName('a/b\\c"d')).toBe('a_b_c_d.pdf');
  });

  it('falls back when the scenario has no name', () => {
    expect(pdfFileName(undefined)).toBe('flowchart.pdf');
    expect(pdfFileName('   ')).toBe('flowchart.pdf');
  });
});

describe('exportFlowchartToPdf', () => {
  const viewport = { className: 'react-flow__viewport' } as unknown as HTMLElement;

  it('captures the whole graph, not the transform the user left the canvas on', async () => {
    const toImage = vi.fn().mockResolvedValue('data:image/png;base64,xxx');
    const pdf = fakePdf();

    await exportFlowchartToPdf({
      viewport, nodes, scenarioName: 'Flow',
      toImage: toImage as never,
      createPdf: (orientation) => { pdf.orientation = orientation; return pdf as never; },
    });

    const [element, options] = toImage.mock.calls[0];
    expect(element).toBe(viewport);
    // bounds are 360 x 460 -> padded, and the style transform is the fitted one
    expect(options.width).toBe(360 + GRAPH_PADDING * 2);
    expect(options.height).toBe(460 + GRAPH_PADDING * 2);
    expect(options.style.transform).toMatch(/^translate\(-?[\d.]+px, -?[\d.]+px\) scale\([\d.]+\)$/);
    expect(options.style.width).toBe(`${options.width}px`);
  });

  it('renders on the canvas background at print resolution', async () => {
    const toImage = vi.fn().mockResolvedValue('data:image/png;base64,xxx');
    await exportFlowchartToPdf({
      viewport, nodes, scenarioName: 'Flow',
      toImage: toImage as never,
      createPdf: () => fakePdf() as never,
    });
    const options = toImage.mock.calls[0][1];
    expect(options.backgroundColor).toBe(CANVAS_BACKGROUND);
    expect(options.pixelRatio).toBe(PIXEL_RATIO);
  });

  it('leaves the canvas chrome out of the picture', async () => {
    const toImage = vi.fn().mockResolvedValue('data:image/png;base64,xxx');
    await exportFlowchartToPdf({
      viewport, nodes, scenarioName: 'Flow',
      toImage: toImage as never,
      createPdf: () => fakePdf() as never,
    });
    const { filter } = toImage.mock.calls[0][1];

    const controls = Object.assign(document.createElement('div'), { className: 'react-flow__controls' });
    const panel = Object.assign(document.createElement('div'), { className: 'react-flow__panel top right' });
    const node = Object.assign(document.createElement('div'), { className: 'react-flow__node' });
    expect(filter(controls)).toBe(false);
    expect(filter(panel)).toBe(false);
    expect(filter(node)).toBe(true);
  });

  it('picks the page orientation from the shape of the graph', async () => {
    const toImage = vi.fn().mockResolvedValue('data:image/png;base64,xxx');
    const orientations: string[] = [];
    const run = (ns: Node[]) =>
      exportFlowchartToPdf({
        viewport, nodes: ns, scenarioName: 'Flow',
        toImage: toImage as never,
        createPdf: (o) => { orientations.push(o); return fakePdf() as never; },
      });

    await run(nodes); // 360 x 460 -> portrait
    const wide = [
      { id: 'a', position: { x: 0, y: 0 }, data: {}, measured: { width: 160, height: 60 } },
      { id: 'b', position: { x: 1200, y: 0 }, data: {}, measured: { width: 160, height: 60 } },
    ] as unknown as Node[];
    await run(wide); // 1360 x 60 -> landscape

    expect(orientations).toEqual(['portrait', 'landscape']);
  });

  it('saves one page named after the scenario', async () => {
    const pdf = fakePdf();
    const name = await exportFlowchartToPdf({
      viewport, nodes, scenarioName: 'NOrder/MKT',
      toImage: vi.fn().mockResolvedValue('data:image/png;base64,xxx') as never,
      createPdf: () => pdf as never,
    });

    expect(pdf.added).toHaveLength(1);
    expect((pdf.added[0] as unknown[])[1]).toBe('PNG');
    expect(pdf.saved).toBe('NOrder_MKT.pdf');
    expect(name).toBe('NOrder_MKT.pdf');
  });

  it('refuses to export an empty canvas', async () => {
    await expect(
      exportFlowchartToPdf({
        viewport, nodes: [], scenarioName: 'Flow',
        toImage: vi.fn() as never,
        createPdf: () => fakePdf() as never,
      }),
    ).rejects.toThrow(/no nodes/);
  });
});
