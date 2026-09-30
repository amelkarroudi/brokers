"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import useSWR from "swr";
import { Car, Plus } from "lucide-react";

import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { VehicleForm } from "@/components/vehicle-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import type { Page, Vehicle, VehicleSummary } from "@/lib/types";

export default function VehiclesPage() {
  const router = useRouter();
  const [status, setStatus] = useState("ALL");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (status !== "ALL") {
    params.set("status", status);
  }
  if (search.trim()) {
    params.set("search", search.trim());
  }
  const { data } = useSWR<Page<VehicleSummary>>(`/api/vehicles?${params}`);

  return (
    <>
      <PageHeader
        title="Fleet"
        description="Every car here is the single source of truth for its listings on all channels."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus /> Add car
          </Button>
        }
      />

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search reference, make or model"
          className="max-w-xs"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
        />
        <Select
          value={status}
          onValueChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All statuses</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="ARCHIVED">Archived</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {data && data.totalItems === 0 ? (
        <EmptyState
          title="No cars found"
          description="Add a car, give it a photo and publish it to put it on sale on every connected channel."
        />
      ) : (
        <Card className="py-0">
          <CardContent className="px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-4">Car</TableHead>
                  <TableHead>Reference</TableHead>
                  <TableHead>Class</TableHead>
                  <TableHead>Daily rate</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data?.items ?? []).map((vehicle) => (
                  <TableRow key={vehicle.id} className="cursor-pointer" onClick={() => router.push(`/vehicles/${vehicle.id}`)}>
                    <TableCell className="pl-4">
                      <div className="flex items-center gap-3">
                        {vehicle.coverPhotoUrl ? (
                          <img src={vehicle.coverPhotoUrl} alt="" className="h-10 w-14 rounded object-cover" />
                        ) : (
                          <div className="bg-muted flex h-10 w-14 items-center justify-center rounded">
                            <Car className="text-muted-foreground size-4" />
                          </div>
                        )}
                        <Link href={`/vehicles/${vehicle.id}`} className="font-medium">
                          {vehicle.make} {vehicle.model} <span className="text-muted-foreground">{vehicle.year}</span>
                        </Link>
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{vehicle.reference}</TableCell>
                    <TableCell className="font-mono text-xs">{vehicle.acrissCode}</TableCell>
                    <TableCell>{formatMoney(vehicle.dailyRate, vehicle.currency)}</TableCell>
                    <TableCell>
                      <StatusBadge status={vehicle.status} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}

      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Add a car</DialogTitle>
            <DialogDescription>The car starts as a draft. Add photos and publish it from its page.</DialogDescription>
          </DialogHeader>
          <VehicleForm
            submitLabel="Create car"
            onSubmit={async (body) => {
              const created = await api<Vehicle>("/api/vehicles", "POST", body);
              router.push(`/vehicles/${created.id}`);
            }}
          />
        </DialogContent>
      </Dialog>
    </>
  );
}
