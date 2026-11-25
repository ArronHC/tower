export class APIError extends Error {
  constructor(readonly status: number, message: string, readonly code?: string, readonly details?: unknown) {
    super(message);
  }
}

export function required<T>(value: T | undefined | null, message = 'Not found'): T {
  if (value == null) throw new APIError(404, message, 'NOT_FOUND');
  return value;
}

export function invalid(message: string): never {
  throw new APIError(400, message, 'INVALID_INPUT');
}
