import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import { useToast, type ToastVariant } from '@/hooks/use-toast';
import { cn } from '@/lib/utils';

const ICONS: Record<ToastVariant, typeof Info> = {
  success: CheckCircle2,
  error: AlertCircle,
  info: Info,
};

const STYLES: Record<ToastVariant, string> = {
  success: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
  error: 'border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-300',
  info: 'border-[var(--border-strong)] bg-[var(--surface-overlay)] text-[var(--text-primary)]',
};

/**
 * Renders queued notifications.
 *
 * The region is a polite live region so announcements do not interrupt a screen
 * reader mid-sentence; errors use `alert` so they are announced immediately.
 */
export function Toaster() {
  const { toasts, dismiss } = useToast();

  if (toasts.length === 0) return null;

  return (
    <div
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
      aria-live="polite"
      aria-atomic="false"
    >
      {toasts.map((toast) => {
        const Icon = ICONS[toast.variant];
        return (
          <div
            key={toast.id}
            role={toast.variant === 'error' ? 'alert' : 'status'}
            className={cn(
              'pointer-events-auto flex animate-[slide-up_0.35s_cubic-bezier(0.16,1,0.3,1)] items-start gap-3 rounded-lg border p-3 shadow-lg backdrop-blur',
              STYLES[toast.variant],
            )}
          >
            <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
            <p className="flex-1 text-sm font-medium">{toast.message}</p>
            <button
              type="button"
              onClick={() => dismiss(toast.id)}
              className="rounded p-0.5 opacity-70 transition-opacity hover:opacity-100"
              aria-label="Dismiss notification"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
