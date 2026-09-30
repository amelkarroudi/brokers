export function formatDateTime(value?: string | null): string {
  if (!value) {
    return "—";
  }
  return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function formatMoney(amount?: number | null, currency?: string | null): string {
  if (amount === undefined || amount === null || !currency) {
    return "—";
  }
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amount);
}

export function channelName(channel: string): string {
  return channel === "BOOKING" ? "Booking.com" : "Rentalcars.com";
}

export function humanize(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replaceAll("_", " ");
}

/** Converts an ISO instant to the value of a datetime-local input (local time, minutes). */
export function toLocalInput(iso: string): string {
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

/** Converts a datetime-local input value to an ISO instant. */
export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}
