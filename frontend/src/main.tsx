import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { AuthProvider } from '@/hooks/use-auth';
import { ThemeProvider } from '@/hooks/use-theme';
import { ToastProvider } from '@/hooks/use-toast';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { ApiError } from '@/lib/api-client';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      // Refetching on every tab focus is noisy for a catalogue that changes
      // slowly, and it makes the UI feel jumpy.
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Never retry a client error: the request is wrong, not unlucky.
        if (error instanceof ApiError && !error.isRetryable) return false;
        return failureCount < 2;
      },
      retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
    },
    mutations: {
      // Mutations are not idempotent; a blind retry could double-submit.
      retry: false,
    },
  },
});

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element #root was not found in the document');
}

createRoot(container).render(
  <StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <ToastProvider>
            <BrowserRouter>
              <AuthProvider>
                <App />
              </AuthProvider>
            </BrowserRouter>
          </ToastProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  </StrictMode>,
);
