import { HttpErrorResponse } from '@angular/common/http';

/** User-facing message for list/detail load failures. */
export function describeHttpLoadError(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the API. Is the backend running?';
    }
    if (error.status === 503) {
      return 'The API is temporarily unavailable. Please try again.';
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return fallback;
}
