import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from './Button';

interface Props {
  children: ReactNode;
  /** Optional replacement UI. */
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches render-time exceptions so one broken subtree does not blank the page.
 *
 * Must remain a class component: React exposes no hook equivalent of
 * `componentDidCatch`.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // In a real deployment this is where an error reporter would be called.
    // eslint-disable-next-line no-console
    console.error('Unhandled render error:', error, errorInfo.componentStack);
  }

  private reset = (): void => {
    this.setState({ error: null });
  };

  render(): ReactNode {
    if (!this.state.error) return this.props.children;
    if (this.props.fallback) return this.props.fallback;

    return (
      <div
        role="alert"
        className="flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-[var(--border-subtle)] px-6 py-16 text-center"
      >
        <div className="mb-4 rounded-full bg-red-500/10 p-4">
          <AlertTriangle className="h-8 w-8 text-red-500" aria-hidden="true" />
        </div>
        <h2 className="mb-2 text-lg font-semibold">Something went wrong</h2>
        <p className="mb-6 max-w-sm text-sm text-[var(--text-secondary)]">
          This section failed to load. The rest of the page is unaffected.
        </p>
        <Button onClick={this.reset} variant="secondary">
          <RotateCcw className="h-4 w-4" aria-hidden="true" />
          Try again
        </Button>
      </div>
    );
  }
}
