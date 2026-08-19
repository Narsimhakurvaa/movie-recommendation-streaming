import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: ReactNode;
}

/**
 * Shown when a collection is legitimately empty.
 *
 * Always pairs the explanation with a way forward: an empty screen that only
 * says "nothing here" leaves the user stuck.
 */
export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-[var(--border-subtle)] px-6 py-16 text-center">
      <div className="mb-4 rounded-full bg-[var(--surface-sunken)] p-4">
        <Icon className="h-8 w-8 text-[var(--text-muted)]" aria-hidden="true" />
      </div>
      <h3 className="mb-2 text-lg font-semibold text-[var(--text-primary)]">{title}</h3>
      <p className="mb-6 max-w-sm text-sm text-[var(--text-secondary)]">{description}</p>
      {action}
    </div>
  );
}
