/**
 * Browser APIs that jsdom (the simulated browser the unit tests run in) doesn't provide, but PrimeNG calls.
 * Simple no-op versions are enough: the tests check behaviour, not layout.
 */
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

class NoopObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
  takeRecords(): [] {
    return [];
  }
}
globalThis.ResizeObserver ??= NoopObserver as unknown as typeof ResizeObserver;
globalThis.IntersectionObserver ??= NoopObserver as unknown as typeof IntersectionObserver;

Element.prototype.scrollIntoView ??= () => undefined;
window.scrollTo = () => undefined;
URL.createObjectURL ??= () => 'blob:test';
URL.revokeObjectURL ??= () => undefined;
