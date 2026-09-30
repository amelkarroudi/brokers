"use client";

import { useState } from "react";
import { toast } from "sonner";
import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";

import { Field } from "@/components/field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api, errorMessage } from "@/lib/api";
import type { Vehicle } from "@/lib/types";

/** Photos in display order. The first photo is the cover on every channel. */
export function PhotosPanel({ vehicle, onChange }: { vehicle: Vehicle; onChange: (vehicle: Vehicle) => void }) {
  const [submitting, setSubmitting] = useState(false);
  const base = `/api/vehicles/${vehicle.id}/photos`;
  const editable = vehicle.status !== "ARCHIVED";

  async function run(request: Promise<Vehicle>) {
    try {
      onChange(await request);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  async function onAdd(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSubmitting(true);
    await run(api<Vehicle>(base, "POST", { url: data.get("url"), caption: data.get("caption") || undefined }));
    setSubmitting(false);
    form.reset();
  }

  function move(index: number, offset: number) {
    const ids = vehicle.photos.map((photo) => photo.id);
    const [moved] = ids.splice(index, 1);
    ids.splice(index + offset, 0, moved);
    run(api<Vehicle>(`${base}/order`, "PUT", { photoIds: ids }));
  }

  return (
    <div className="grid gap-4">
      {editable && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Add a photo</CardTitle>
            <CardDescription>Use a public https URL. Channels download photos from this address.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={onAdd} className="grid gap-4 sm:grid-cols-[2fr_1fr_auto] sm:items-end">
              <Field label="Image URL" htmlFor="url">
                <Input id="url" name="url" type="url" placeholder="https://…/car.jpg" required />
              </Field>
              <Field label="Caption" htmlFor="caption">
                <Input id="caption" name="caption" maxLength={200} />
              </Field>
              <Button type="submit" disabled={submitting}>
                Add photo
              </Button>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {vehicle.photos.map((photo, index) => (
          <Card key={photo.id} className="gap-3 overflow-hidden pt-0">
            {/* Photos are hosted anywhere, so a plain img avoids configuring remote image domains. */}
            <img src={photo.url} alt={photo.caption ?? ""} className="aspect-video w-full object-cover" />
            <CardContent className="flex items-center justify-between gap-2">
              <div className="min-w-0">
                {index === 0 && <Badge className="mb-1">Cover</Badge>}
                <p className="text-muted-foreground truncate text-xs">{photo.caption ?? photo.url}</p>
              </div>
              {editable && (
                <div className="flex shrink-0">
                  <Button size="icon" variant="ghost" disabled={index === 0} onClick={() => move(index, -1)}>
                    <ArrowUp />
                  </Button>
                  <Button size="icon" variant="ghost" disabled={index === vehicle.photos.length - 1} onClick={() => move(index, 1)}>
                    <ArrowDown />
                  </Button>
                  <Button size="icon" variant="ghost" onClick={() => run(api<Vehicle>(`${base}/${photo.id}`, "DELETE"))}>
                    <Trash2 />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
