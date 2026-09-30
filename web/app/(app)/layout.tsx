"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { SWRConfig } from "swr";

import { AppSidebar } from "@/components/app-sidebar";
import { Skeleton } from "@/components/ui/skeleton";
import { fetcher } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { me, loading } = useAuth();

  useEffect(() => {
    if (!loading && !me) {
      router.replace("/login");
    }
  }, [loading, me, router]);

  if (loading || !me) {
    return (
      <div className="space-y-4 p-8">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <AppSidebar />
      <main className="flex-1 overflow-y-auto md:h-screen">
        <div className="mx-auto max-w-6xl space-y-6 p-6 md:p-8">
          <SWRConfig value={{ fetcher }}>{children}</SWRConfig>
        </div>
      </main>
    </div>
  );
}
