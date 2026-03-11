import {useEffect, useState} from "react";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";

interface PendingUser {
    id: string;
    email: string;
    username: string;
    presentationMessage: string;
    createdAt: string;
}

export function PendingUsersPage() {
    const [users, setUsers] = useState<PendingUser[]>([]);
    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState<string | null>(null);

    async function fetchPending() {
        try {
            const {data} = await api.get("/admin/users/pending?size=50&sort=createdAt,asc");
            setUsers(data.content);
        } catch {
            toast.error("Falha ao carregar solicitações pendentes");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        fetchPending();
    }, []);

    async function handleApprove(id: string) {
        setActionLoading(id + "-approve");
        try {
            await api.post(`/admin/users/${id}/approve`, {});
            toast.success("Usuário aprovado");
            setUsers((prev) => prev.filter((u) => u.id !== id));
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Falha ao aprovar");
        } finally {
            setActionLoading(null);
        }
    }

    async function handleReject(id: string) {
        const reason = window.prompt("Motivo da rejeição (opcional):");
        if (reason === null) return;

        setActionLoading(id + "-reject");
        try {
            await api.post(`/admin/users/${id}/reject`, {reason});
            toast.success("Usuário rejeitado");
            setUsers((prev) => prev.filter((u) => u.id !== id));
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Falha ao rejeitar");
        } finally {
            setActionLoading(null);
        }
    }

    return (
        <div className="max-w-4xl mx-auto px-4 md:px-6 py-8">
            <div className="flex items-center gap-3 mb-6">
                <h2 className="text-2xl font-bold">Aprovações pendentes</h2>
                <Badge variant="secondary">{users.length}</Badge>
            </div>

            {loading && <p className="text-muted-foreground">Carregando…</p>}

            {!loading && users.length === 0 && (
                <Card>
                    <CardContent className="py-12 text-center text-muted-foreground">
                        Nenhuma solicitação pendente
                    </CardContent>
                </Card>
            )}

            <div className="space-y-4">
                {users.map((user) => (
                    <Card key={user.id}>
                        <CardHeader className="pb-2">
                            <div className="flex items-start justify-between gap-2">
                                <div>
                                    <CardTitle className="text-base">{user.username}</CardTitle>
                                    <p className="text-sm text-muted-foreground">{user.email}</p>
                                </div>
                                <span className="text-xs text-muted-foreground shrink-0">
                                    {new Date(user.createdAt).toLocaleDateString("pt-BR")}
                                </span>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <p className="text-sm mb-4 text-foreground/80">
                                "{user.presentationMessage}"
                            </p>
                            <div className="flex gap-2">
                                <Button
                                    size="sm"
                                    onClick={() => handleApprove(user.id)}
                                    disabled={actionLoading !== null}
                                >
                                    {actionLoading === user.id + "-approve" ? "Aprovando…" : "Aprovar"}
                                </Button>
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    onClick={() => handleReject(user.id)}
                                    disabled={actionLoading !== null}
                                >
                                    {actionLoading === user.id + "-reject" ? "Rejeitando…" : "Rejeitar"}
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>
        </div>
    );
}
