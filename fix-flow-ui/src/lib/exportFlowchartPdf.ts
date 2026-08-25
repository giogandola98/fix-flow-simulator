import { getNodesBounds, type Node } from '@xyflow/react';
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
/** A printed page is white: the dark canvas would flood the sheet with ink. */
export const PAGE_BACKGROUND = '#ffffff';
/** Class that repaints the flowchart for print; see `index.css`. */
export const PRINT_CLASS = 'fixflow-pdf-light';

export interface Placement {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CaptureGeometry {
  width: number;
  height: number;
  transform: { x: number; y: number; zoom: number };
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
 * Bitmap size for a graph, and the viewport transform that puts the graph in it.
 *
 * <p>The image is the graph plus its padding, at zoom 1, scaled down only when a side would exceed
 * {@link MAX_IMAGE_SIDE}. The transform then simply moves the graph's top-left corner to the
 * padding offset — so the graph fills the picture.
 *
 * <p>This used to delegate to React Flow's `getViewportForBounds`, whose `padding` argument is a
 * <em>ratio</em> (0.1 = 10%), not pixels. Passing the pixel padding meant asking for 4800% padding,
 * which left about 2% of the width for the graph: the exported PDF came out zoomed far out with
 * the flowchart tiny in the middle (issue #97).
 */
export function captureGeometry(boundsX: number, boundsY: number, boundsWidth: number, boundsHeight: number): CaptureGeometry {
  const rawWidth = Math.max(boundsWidth, 1) + GRAPH_PADDING * 2;
  const rawHeight = Math.max(boundsHeight, 1) + GRAPH_PADDING * 2;
  const zoom = Math.min(1, MAX_IMAGE_SIDE / Math.max(rawWidth, rawHeight));
  return {
    width: Math.round(rawWidth * zoom),
    height: Math.round(rawHeight * zoom),
    transform: {
      x: (GRAPH_PADDING - boundsX) * zoom,
      y: (GRAPH_PADDING - boundsY) * zoom,
      zoom,
    },
  };
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
 *
 * <p>It is also repainted for print while the picture is taken — the editor is dark, a sheet of
 * paper is not.
 */
export async function exportFlowchartToPdf({
  viewport,
  nodes,
  scenarioName,
  toImage = toPng,
  // compress: a full-page bitmap goes in uncompressed otherwise, which turns a flowchart into
  // an ~8 MB attachment
  createPdf = (orientation) => new jsPDF({ orientation, unit: 'mm', format: 'a4', compress: true }),
}: ExportFlowchartOptions): Promise<string> {
  if (nodes.length === 0) throw new Error('nothing to export: the canvas has no nodes');

  const bounds = getNodesBounds(nodes);
  const { width: imageWidth, height: imageHeight, transform } =
    captureGeometry(bounds.x, bounds.y, bounds.width, bounds.height);

  viewport.classList.add(PRINT_CLASS);
  let dataUrl: string;
  try {
    dataUrl = await toImage(viewport, {
      backgroundColor: PAGE_BACKGROUND,
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
  } finally {
    // Never leave the editor repainted, whatever the capture did.
    viewport.classList.remove(PRINT_CLASS);
  }

  const pdf = createPdf(imageWidth >= imageHeight ? 'landscape' : 'portrait');
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  const { x, y, width, height } = fitToPage(imageWidth, imageHeight, pageWidth, pageHeight);
  pdf.addImage(dataUrl, 'PNG', x, y, width, height);

  const fileName = pdfFileName(scenarioName);
  pdf.save(fileName);
  return fileName;
}
