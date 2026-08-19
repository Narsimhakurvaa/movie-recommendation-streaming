import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { Button } from './Button';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Accessible dialog.
 *
 * Built on the native `<dialog>` element, which gives focus trapping, inert
 * background content and Escape handling from the platform rather than from
 * hand-rolled key listeners that are easy to get subtly wrong.
 */
export function Modal({ open, onClose, title, description, children, footer }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      // showModal (not show) is what makes the rest of the page inert.
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  // The native Escape key closes the dialog directly; mirror that into state.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const handleCancel = (event: Event) => {
      event.preventDefault();
      onClose();
    };
    dialog.addEventListener('cancel', handleCancel);
    return () => dialog.removeEventListener('cancel', handleCancel);
  }, [onClose]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="modal-title"
      aria-describedby={description ? 'modal-description' : undefined}
      className="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-0 text-[var(--text-primary)] shadow-2xl backdrop:bg-black/60 backdrop:backdrop-blur-sm"
      onClick={(event) => {
        // Clicking the backdrop closes; clicks inside the panel must not.
        if (event.target === dialogRef.current) onClose();
      }}
    >
      <div className="flex items-start justify-between gap-4 border-b border-[var(--border-subtle)] p-5">
        <div>
          <h2 id="modal-title" className="text-lg font-semibold">
            {title}
          </h2>
          {description ? (
            <p id="modal-description" className="mt-1 text-sm text-[var(--text-secondary)]">
              {description}
            </p>
          ) : null}
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close dialog">
          <X className="h-5 w-5" aria-hidden="true" />
        </Button>
      </div>
      <div className="p-5">{children}</div>
      {footer ? (
        <div className="flex justify-end gap-3 border-t border-[var(--border-subtle)] p-5">
          {footer}
        </div>
      ) : null}
    </dialog>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  destructive?: boolean;
  isLoading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Confirmation prompt for irreversible actions. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  destructive = false,
  isLoading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={isLoading}>
            Cancel
          </Button>
          <Button
            variant={destructive ? 'danger' : 'primary'}
            onClick={onConfirm}
            isLoading={isLoading}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-sm text-[var(--text-secondary)]">{message}</p>
    </Modal>
  );
}
