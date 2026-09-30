// Thin fetch wrapper around the Brokers API. Errors are RFC 9457 problem details and are
// surfaced as ApiError so forms can show field errors and pages can switch on `code`.

const TOKEN_KEY = "brokers.token";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token === null) {
    window.localStorage.removeItem(TOKEN_KEY);
    return;
  }
  window.localStorage.setItem(TOKEN_KEY, token);
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

export async function api<T>(path: string, method: Method = "GET", body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && token) {
    setToken(null);
    window.dispatchEvent(new Event("brokers:unauthenticated"));
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** SWR fetcher: every key is an API path. */
export const fetcher = <T,>(path: string) => api<T>(path);

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = await response.json();
    return new ApiError(
      response.status,
      problem.code ?? "error",
      problem.detail ?? response.statusText,
      problem.errors ?? {},
    );
  } catch {
    return new ApiError(response.status, "error", response.statusText || "Request failed");
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = Object.entries(error.fieldErrors).map(([field, message]) => `${field} ${message}`);
    return fields.length > 0 ? `${error.message}: ${fields.join(", ")}` : error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Something went wrong";
}
