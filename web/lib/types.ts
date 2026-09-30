// Shapes returned by the Brokers API. Kept in one place so pages stay in sync with the backend.

export type Channel = "BOOKING" | "RENTALCARS";
export type VehicleStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type ListingStatus = "PENDING" | "PUBLISHED" | "FAILED" | "UNPUBLISHED";
export type ConnectionStatus = "NOT_CONNECTED" | "CONNECTED" | "INVALID_CREDENTIALS" | "DISCONNECTED";
export type SyncJobStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type WebhookEventStatus = "RECEIVED" | "PROCESSED" | "IGNORED" | "FAILED";
export type ReservationStatus = "CONFIRMED" | "CANCELLED";
export type BlockReason = "RESERVATION" | "MAINTENANCE" | "MANUAL";
export type Transmission = "MANUAL" | "AUTOMATIC";
export type FuelType = "PETROL" | "DIESEL" | "HYBRID" | "ELECTRIC" | "LPG";

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Me {
  member: { id: string; email: string; fullName: string; role: string; lastLoginAt: string | null };
  organization: { id: string; name: string; slug: string; defaultCurrency: string; timezone: string; status: string };
}

export interface Session {
  token: string;
  expiresAt: string;
  me: Me;
}

export interface Connection {
  channel: Channel;
  status: ConnectionStatus;
  connectionId?: string;
  accountId?: string;
  apiKeyHint?: string;
  webhookUrl?: string;
  lastVerifiedAt?: string;
  lastError?: string;
  updatedAt?: string;
}

export interface ConnectionSecret {
  connection: Connection;
  webhookSecret?: string;
}

export interface Location {
  id: string;
  code: string;
  name: string;
  addressLine: string;
  city: string;
  postalCode?: string;
  countryCode: string;
  latitude?: number;
  longitude?: number;
  iataCode?: string;
}

export interface Photo {
  id: string;
  url: string;
  caption?: string;
  position: number;
}

export interface Listing {
  channel: Channel;
  status: ListingStatus;
  externalId?: string;
  lastSyncedAt?: string;
  lastError?: string;
}

export interface VehicleSummary {
  id: string;
  reference: string;
  make: string;
  model: string;
  year: number;
  acrissCode: string;
  dailyRate: number;
  currency: string;
  status: VehicleStatus;
  locationId: string;
  coverPhotoUrl?: string;
  updatedAt: string;
}

export interface Vehicle {
  id: string;
  reference: string;
  make: string;
  model: string;
  year: number;
  acrissCode: string;
  transmission: Transmission;
  fuelType: FuelType;
  seats: number;
  doors: number;
  bags: number;
  airConditioning: boolean;
  mileageLimitKm?: number;
  minDriverAge: number;
  dailyRate: number;
  deposit?: number;
  currency: string;
  status: VehicleStatus;
  location: { id: string; code: string; name: string; city: string };
  photos: Photo[];
  listings: Listing[];
  createdAt: string;
  updatedAt: string;
}

export interface AvailabilityBlock {
  id: string;
  startsAt: string;
  endsAt: string;
  reason: BlockReason;
  reservationId?: string;
  note?: string;
}

export interface Reservation {
  id: string;
  vehicleId: string;
  channel: Channel;
  externalReservationId: string;
  status: ReservationStatus;
  pickupAt: string;
  returnAt: string;
  customerName?: string;
  customerEmail?: string;
  totalAmount?: number;
  currency?: string;
  hasConflict: boolean;
  createdAt: string;
}

export interface SyncJob {
  id: string;
  vehicleId: string;
  channel: Channel;
  type: "UPSERT_LISTING" | "REPLACE_PHOTOS" | "REPLACE_AVAILABILITY" | "DEACTIVATE_LISTING";
  status: SyncJobStatus;
  trigger: string;
  force: boolean;
  attempts: number;
  maxAttempts: number;
  nextAttemptAt: string;
  lastError?: string;
  createdAt: string;
  completedAt?: string;
}

export interface WebhookEvent {
  id: string;
  channel: Channel;
  externalEventId: string;
  eventType: string;
  status: WebhookEventStatus;
  attempts: number;
  lastError?: string;
  receivedAt: string;
  processedAt?: string;
  payload?: string;
}

export interface Dashboard {
  vehicles: { draft: number; active: number; archived: number };
  connections: { channel: Channel; status: ConnectionStatus }[];
  listings: { published: number; pending: number; failed: number };
  reservations: { upcoming: number; conflicts: number };
  sync: { queued: number; failed: number };
  failedWebhooks: number;
}

export interface SandboxListing {
  externalId: string;
  active: boolean;
  updatedAt: string;
  content: unknown;
  photos?: unknown;
  availability?: unknown;
}
