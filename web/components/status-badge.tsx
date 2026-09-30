import { Badge } from "@/components/ui/badge";
import { humanize } from "@/lib/format";

type Tone = "success" | "warning" | "destructive" | "secondary" | "outline";

const TONES: Record<string, Tone> = {
  ACTIVE: "success",
  CONNECTED: "success",
  PUBLISHED: "success",
  SUCCEEDED: "success",
  PROCESSED: "success",
  CONFIRMED: "success",
  PENDING: "warning",
  RUNNING: "warning",
  RECEIVED: "warning",
  DRAFT: "secondary",
  IGNORED: "secondary",
  UNPUBLISHED: "secondary",
  NOT_CONNECTED: "outline",
  DISCONNECTED: "outline",
  ARCHIVED: "outline",
  CANCELLED: "outline",
  FAILED: "destructive",
  INVALID_CREDENTIALS: "destructive",
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={TONES[status] ?? "secondary"}>{humanize(status)}</Badge>;
}
