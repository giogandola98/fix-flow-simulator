// Shared status/selection → ring highlight class for canvas nodes.
export function ringClass(status: string | undefined, selected: boolean | undefined): string {
  return status === 'running'
    ? 'animate-pulse ring-2 ring-green-400'
    : status === 'passed'
      ? 'ring-2 ring-green-500'
      : status === 'failed'
        ? 'ring-2 ring-red-500'
        : selected
          ? 'ring-2 ring-blue-400'
          : '';
}
