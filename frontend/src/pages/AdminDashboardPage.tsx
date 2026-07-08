import {useEffect, useState} from "react";
import {getDashboard} from "@/api/adminApi";
import type {DashboardData} from "@/types/admin";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Users, HardDrive, Database} from "lucide-react";

export function AdminDashboardPage() {
    const [data, setData] = useState<DashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        getDashboard()
            .then((data) => setData(data))
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return (
            <div className="max-w-4xl mx-auto px-4 md:px-6 py-8 space-y-6">
                <h1 className="text-xl font-semibold">Dashboard</h1>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {[1, 2].map(i => (
                        <div key={i} className="h-28 rounded-xl bg-muted animate-pulse"/>
                    ))}
                </div>
                <div className="h-64 rounded-xl bg-muted animate-pulse"/>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="max-w-4xl mx-auto px-4 md:px-6 py-8">
                <p className="text-sm text-muted-foreground">Erro ao carregar dashboard.</p>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto px-4 md:px-6 py-8 space-y-6">
            <h1 className="text-xl font-semibold">Dashboard</h1>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
                        <CardTitle className="text-sm font-medium text-muted-foreground">
                            Usuários ativos
                        </CardTitle>
                        <Users className="w-4 h-4 text-muted-foreground"/>
                    </CardHeader>
                    <CardContent>
                        <p className="text-3xl font-bold">{data.activeUsers}</p>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
                        <CardTitle className="text-sm font-medium text-muted-foreground">
                            Storage total
                        </CardTitle>
                        <HardDrive className="w-4 h-4 text-muted-foreground"/>
                    </CardHeader>
                    <CardContent>
                        <p className="text-3xl font-bold">{Number(data.totalStorageUsedGb).toFixed(2)} GB</p>
                    </CardContent>
                </Card>
            </div>

            <Card>
                <CardHeader className="flex flex-row items-center gap-2 pb-3">
                    <Database className="w-4 h-4 text-muted-foreground"/>
                    <CardTitle className="text-sm font-medium">Storage por usuário</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                    {data.storageByUser.length === 0 ? (
                        <p className="text-sm text-muted-foreground px-6 pb-6">
                            Nenhum arquivo armazenado ainda.
                        </p>
                    ) : (
                        <div className="divide-y">
                            {data.storageByUser.map((u) => {
                                const quota = Number(u.quotaGb);
                                const used = Number(u.usedGb);
                                const pct = quota > 0 ? Math.min((used / quota) * 100, 100) : 0;
                                const barColor = pct >= 90
                                    ? "bg-destructive"
                                    : pct >= 70
                                        ? "bg-yellow-500"
                                        : "bg-primary";

                                return (
                                    <div key={u.userId} className="flex items-center gap-3 px-6 py-3">
                                        <span className="text-sm font-medium w-32 truncate">
                                            {u.username}
                                        </span>
                                        <div className="flex-1 h-2 rounded-full bg-muted overflow-hidden">
                                            <div
                                                className={`h-full rounded-full transition-all ${barColor}`}
                                                style={{width: `${pct}%`}}
                                            />
                                        </div>
                                        <span className="text-sm text-muted-foreground w-32 text-right">
                                            {used.toFixed(2)} / {quota.toFixed(2)} GB
                                        </span>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}