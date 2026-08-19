import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Unmount between tests so state never leaks across cases.
afterEach(() => {
  cleanup();
  window.localStorage.clear();
  vi.clearAllMocks();
});

/*
 * jsdom implements neither of these, and both are used by production code
 * (the theme provider and the movie rails respectively).
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
});

Element.prototype.scrollBy = vi.fn();
Element.prototype.scrollIntoView = vi.fn();
window.scrollTo = vi.fn();

// The native <dialog> API is unimplemented in jsdom; the Modal relies on it.
HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
  this.open = true;
});
HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
  this.open = false;
});
