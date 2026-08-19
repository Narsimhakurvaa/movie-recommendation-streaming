import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { MessageSquare, Pencil, Trash2 } from 'lucide-react';
import { movieService } from '@/services/movies';
import { reviewService } from '@/services/interactions';
import { useAuth } from '@/hooks/use-auth';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/Modal';
import { EmptyState } from '@/components/ui/EmptyState';
import { StarRating } from '@/components/ui/Rating';
import { Skeleton } from '@/components/ui/Skeleton';
import { ApiError } from '@/lib/api-client';
import { formatRelativeTime } from '@/lib/utils';
import type { Review } from '@/types/api';

/** Matches the backend's review constraints so errors surface before submit. */
const reviewSchema = z.object({
  title: z.string().max(160, 'Title must be 160 characters or fewer').optional(),
  body: z
    .string()
    .min(20, 'Reviews must be at least 20 characters')
    .max(5000, 'Reviews must be 5000 characters or fewer'),
  containsSpoilers: z.boolean().optional(),
});

type ReviewValues = z.infer<typeof reviewSchema>;

export function ReviewSection({ movieId }: { movieId: number }) {
  const { isAuthenticated } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Review | null>(null);
  const [composing, setComposing] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Review | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['movie', movieId, 'reviews'],
    queryFn: () => movieService.reviews(movieId, 0, 10),
  });

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ReviewValues>({ resolver: zodResolver(reviewSchema) });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['movie', movieId, 'reviews'] });
  };

  const submitMutation = useMutation({
    mutationFn: (values: ReviewValues) =>
      editing
        ? reviewService.update(editing.id, { ...values, body: values.body })
        : reviewService.create(movieId, { ...values, body: values.body }),
    onSuccess: () => {
      notify(editing ? 'Review updated' : 'Review published', 'success');
      reset();
      setEditing(null);
      setComposing(false);
      invalidate();
    },
    onError: (error) => {
      // Field-level errors from the server are mapped back onto the form so
      // they appear next to the offending input, not just as a toast.
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors).forEach(([field, message]) => {
          setError(field as keyof ReviewValues, { message });
        });
        notify(error.message, 'error');
      } else {
        notify('Could not save your review', 'error');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (review: Review) => reviewService.remove(review.id),
    onSuccess: () => {
      notify('Review deleted', 'success');
      setPendingDelete(null);
      invalidate();
    },
    onError: () => notify('Could not delete the review', 'error'),
  });

  const reviews = data?.content ?? [];
  const ownReview = reviews.find((review) => review.ownedByCurrentUser);

  const startEditing = (review: Review) => {
    setEditing(review);
    setComposing(true);
    reset({
      title: review.title ?? '',
      body: review.body,
      containsSpoilers: review.containsSpoilers,
    });
  };

  return (
    <section aria-labelledby="reviews-heading">
      <div className="mb-4 flex items-center justify-between gap-4">
        <h2 id="reviews-heading" className="font-[family-name:var(--font-display)] text-xl font-bold">
          Reviews {data ? <span className="text-[var(--text-muted)]">({data.totalElements})</span> : null}
        </h2>
        {isAuthenticated && !ownReview && !composing ? (
          <Button size="sm" onClick={() => setComposing(true)}>
            Write a review
          </Button>
        ) : null}
      </div>

      {composing ? (
        <form
          onSubmit={handleSubmit((values) => submitMutation.mutate(values))}
          className="mb-6 space-y-3 rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4"
          noValidate
        >
          <div>
            <label htmlFor="review-title" className="mb-1.5 block text-sm font-medium">
              Title <span className="font-normal text-[var(--text-muted)]">(optional)</span>
            </label>
            <input
              id="review-title"
              {...register('title')}
              className="h-10 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 text-sm focus-visible:outline-2"
            />
            {errors.title ? (
              <p role="alert" className="mt-1 text-xs text-red-500">
                {errors.title.message}
              </p>
            ) : null}
          </div>

          <div>
            <label htmlFor="review-body" className="mb-1.5 block text-sm font-medium">
              Your review
            </label>
            <textarea
              id="review-body"
              rows={5}
              {...register('body')}
              aria-invalid={Boolean(errors.body)}
              aria-describedby={errors.body ? 'review-body-error' : undefined}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] p-3 text-sm focus-visible:outline-2"
              placeholder="What did you make of it? At least 20 characters."
            />
            {errors.body ? (
              <p id="review-body-error" role="alert" className="mt-1 text-xs text-red-500">
                {errors.body.message}
              </p>
            ) : null}
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" {...register('containsSpoilers')} className="h-4 w-4 rounded" />
            This review contains spoilers
          </label>

          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setComposing(false);
                setEditing(null);
                reset();
              }}
            >
              Cancel
            </Button>
            <Button type="submit" isLoading={isSubmitting || submitMutation.isPending}>
              {editing ? 'Save changes' : 'Publish review'}
            </Button>
          </div>
        </form>
      ) : null}

      {isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-28 w-full rounded-[var(--radius-card)]" />
          <Skeleton className="h-28 w-full rounded-[var(--radius-card)]" />
        </div>
      ) : reviews.length === 0 ? (
        <EmptyState
          icon={MessageSquare}
          title="No reviews yet"
          description="Be the first to share what you thought of this film."
          action={
            isAuthenticated ? (
              <Button onClick={() => setComposing(true)}>Write a review</Button>
            ) : (
              <Link to="/login">
                <Button>Sign in to review</Button>
              </Link>
            )
          }
        />
      ) : (
        <ul className="space-y-4">
          {reviews.map((review) => (
            <li
              key={review.id}
              className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[var(--accent)] text-sm font-bold text-[var(--accent-contrast)]">
                    {review.author.displayName.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <p className="text-sm font-semibold">{review.author.displayName}</p>
                    <p className="text-xs text-[var(--text-muted)]">
                      {formatRelativeTime(review.createdAt)}
                      {review.updatedAt !== review.createdAt ? ' · edited' : ''}
                    </p>
                  </div>
                </div>
                {review.authorRating ? (
                  <StarRating value={review.authorRating} readOnly size="sm" />
                ) : null}
              </div>

              {review.title ? <h3 className="mt-3 font-semibold">{review.title}</h3> : null}

              {review.containsSpoilers ? (
                <details className="mt-2">
                  <summary className="cursor-pointer text-sm font-medium text-[var(--accent)]">
                    Contains spoilers — click to reveal
                  </summary>
                  <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-[var(--text-secondary)]">
                    {review.body}
                  </p>
                </details>
              ) : (
                <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-[var(--text-secondary)]">
                  {review.body}
                </p>
              )}

              {review.ownedByCurrentUser ? (
                <div className="mt-3 flex gap-2">
                  <Button variant="ghost" size="sm" onClick={() => startEditing(review)}>
                    <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                    Edit
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setPendingDelete(review)}>
                    <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                    Delete
                  </Button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this review?"
        message="This cannot be undone."
        confirmLabel="Delete"
        destructive
        isLoading={deleteMutation.isPending}
        onConfirm={() => pendingDelete && deleteMutation.mutate(pendingDelete)}
        onCancel={() => setPendingDelete(null)}
      />
    </section>
  );
}
