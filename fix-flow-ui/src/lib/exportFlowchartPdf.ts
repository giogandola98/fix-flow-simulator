import { getNodesBounds, getViewportForBounds, type Node } from '@xyflow/react';
import { toPng } from 'html-to-image';
import { jsPDF } from 'jspdf';

/** Empty margin kept around the graph, in flow units. */
export const GRAPH_PADDING = 48;
/** Longest side of the rendered bitmap, before the device pixel multiplier. */
export const MAX_IMAGE_SIDE = 2400;
/** Device pixel multiplier: 2 keeps node labels and FIX tags legible in print. */
export const PIXEL_RATIO = 2;
/** Page margin, in millimetres. */
export const PAGE_MARGIN_MM = 10;
/** Canvas background, so the PNG is not transparent — a transparent PNG in a PDF
 *  renders white, and these nodes carry light text. */
export const CANVAS_BACKGROUND = '#0f1117';

export interface Placement {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Fits an image inside a page, centred, preserving its aspect ratio. Never enlarges beyond the
 * printable area; a graph smaller than the page keeps its proportions rather than being stretched.
 */
export function fitToPage(
  imageWidth: number,
  imageHeight: number,
  pageWidth: number,
  pageHeight: number,
  margin = PAGE_MARGIN_MM,
): Placement {
  const printableWidth = Math.max(pageWidth - margin * 2, 1);
  const printableHeight = Math.max(pageHeight - margin * 2, 1);
  const ratio = Math.min(printableWidth / imageWidth, printableHeight / imageHeight);
  const width = imageWidth * ratio;
  const height = imageHeight * ratio;
  return { x: (pageWidth - width) / 2, y: (pageHeight - height) / 2, width, height };
}

/**
 * Bitmap size for a graph of the given bounds: the graph plus its padding, scaled down only when
 * a side would exceed {@link MAX_IMAGE_SIDE}. Deriving it from the bounds — rather than rendering
 * into a fixed-size frame — is what keeps a tall lifecycle flow from being letterboxed into a
 * landscape image with empty bands on either side.
 */
export function imageSizeFor(boundsWidth: number, boundsHeight: number): { width: number; height: number } {
  const rawWidth = Math.max(boundsWidth, 1) + GRAPH_PADDING * 2;
  const rawHeight = Math.max(boundsHeight, 1) + GRAPH_PADDING * 2;
  const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(rawWidth, rawHeight));
  return { width: Math.round(rawWidth * scale), height: Math.round(rawHeight * scale) };
}

/** Mirrors the server's sanitising in `GET /scenarios/{id}/export`. */
export function pdfFileName(scenarioName: string | undefined | null): string {
  const base = (scenarioName ?? '').trim();
  const safe = (base === '' ? 'flowchart' : base).replace(/[\r\n"\\/]+/g, '_');
  return `${safe}.pdf`;
}

/** Canvas chrome must not end up in the middle of the diagram. */
function isChrome(element: HTMLElement): boolean {
  const className = typeof element.className === 'string' ? element.className : '';
  return (
    className.includes('react-flow__panel') ||
    className.includes('react-flow__controls') ||
    className.includes('react-flow__minimap') ||
    className.includes('react-flow__attribution')
  );
}

export interface ExportFlowchartOptions {
  /** The `.react-flow__viewport` element holding the nodes and edges. */
  viewport: HTMLElement;
  /** Measured nodes, as returned by `useReactFlow().getNodes()`. */
  nodes: Node[];
  scenarioName?: string | null;
  /** Seams for tests. */
  toImage?: typeof toPng;
  createPdf?: (orientation: 'portrait' | 'landscape') => jsPDF;
}

/**
 * Renders the whole flowchart to a one-page PDF and hands it to the browser as a download.
 *
 * <p>The capture is taken at a transform that fits the entire graph, not at the transform the user
 * left the canvas on: `.react-flow__viewport` is translated and scaled by the current pan/zoom, so
 * capturing it as-is would export whatever happens to be on screen, cropped at the window edge.
 */
export async function exportFlowchartToPdf({
  viewport,
  nodes,
  scenarioName,
  toImage = toPng,
  createPdf = (orientation) => new jsPDF({ orientation, unit: 'mm', format: 'a4' }),
}: ExportFlowchartOptions): Promise<string> {
  if (nodes.length === 0) throw new Error('nothing to export: the canvas has no nodes');

  const bounds = getNodesBounds(nodes);
  const { width: imageWidth, height: imageHeight } = imageSizeFor(bounds.width, bounds.height);
  const transform = getViewportForBounds(bounds, imageWidth, imageHeight, 0.1, 4, GRAPH_PADDING);

  const dataUrl = await toImage(viewport, {
    backgroundColor: CANVAS_BACKGROUND,
    width: imageWidth,
    height: imageHeight,
    pixelRatio: PIXEL_RATIO,
    filter: (node) => !(node instanceof HTMLElement) || !isChrome(node),
    style: {
      width: `${imageWidth}px`,
      height: `${imageHeight}px`,
      transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.zoom})`,
    },
  });

  const pdf = createPdf(imageWidth >= imageHeight ? 'landscape' : 'portrait');
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  const { x, y, width, height } = fitToPage(imageWidth, imageHeight, pageWidth, pageHeight);
  pdf.addImage(dataUrl, 'PNG', x, y, width, height);

  const fileName = pdfFileName(scenarioName);
  pdf.save(fileName);
  return fileName;
}
