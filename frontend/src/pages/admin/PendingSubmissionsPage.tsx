import {useEffect, useState} from "react";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";

interface PendingSubmission {
    id: string;
    title: string;
    coverUrl: string | null;
    submitterUsername: string;
    submitterEmail: string;
    submittedAt: string;
}

export function PendingSubmissionsPage() {
    const [submissions, setSubmissions] = useState<PendingSubmission[]>([]);
    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState<string | null>(null);

    async function fetchPending() {
        try {
            const {data} = await api.get("/admin/submissions?size=50&sort=submittedAt,asc");
            setSubmissions(data.content);
        } catch {
            toast.error("Falha ao carregar submissões pendentes");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        fetchPending();
    }, []);

    async function handleApprove(id: string) {
        if (!confirm("Aprovar esta publicação? O mangá ficará visível na biblioteca pública.")) return;
        setActionLoading(id + "-approve");
        try {
            await api.post(`/admin/submissions/${id}/approve`);
            toast.success("Mangá publicado na biblioteca!");
            setSubmissions((prev) => prev.filter((s) => s.id !== id));
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
            await api.post(`/admin/submissions/${id}/reject`, {rejectionReason: reason || null});
            toast.success("Submissão rejeitada");
            setSubmissions((prev) => prev.filter((s) => s.id !== id));
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Falha ao rejeitar");
        } finally {
            setActionLoading(null);
        }
    }

    return (
        <div className="max-w-4xl mx-auto px-4 md:px-6 py-8">
            <div className="flex items-center gap-3 mb-6">
                <h2 className="text-2xl font-bold">Submissões pendentes</h2>
                <Badge variant="secondary">{submissions.length}</Badge>
            </div>

            {loading && <p className="text-muted-foreground">Carregando…</p>}

            {!loading && submissions.length === 0 && (
                <Card>
                    <CardContent className="py-12 text-center text-muted-foreground">
                        Nenhuma submissão pendente
                    </CardContent>
                </Card>
            )}

            <div className="space-y-4">
                {submissions.map((sub) => (
                    <Card key={sub.id}>
                        <CardHeader className="pb-2">
                            <div className="flex items-start justify-between gap-2">
                                <div className="flex items-center gap-3">
                                    {sub.coverUrl && (
                                        <img
                                            src={sub.coverUrl}
                                            alt={sub.title}
                                            className="w-10 aspect-[2/3] object-cover rounded border shrink-0"
                                        />
                                    )}
                                    <div>
                                        <CardTitle className="text-base">{sub.title}</CardTitle>
                                        <p className="text-sm text-muted-foreground">
                                            por {sub.submitterUsername} ({sub.submitterEmail})
                                        </p>
                                    </div>
                                </div>
                                <span className="text-xs text-muted-foreground shrink-0">
                                    {new Date(sub.submittedAt).toLocaleDateString("pt-BR")}
                                </span>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <div className="flex gap-2">
                                <Button
                                    size="sm"
                                    onClick={() => handleApprove(sub.id)}
                                    disabled={actionLoading !== null}
                                >
                                    {actionLoading === sub.id + "-approve" ? "Aprovando…" : "Aprovar"}
                                </Button>
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    onClick={() => handleReject(sub.id)}
                                    disabled={actionLoading !== null}
                                >
                                    {actionLoading === sub.id + "-reject" ? "Rejeitando…" : "Rejeitar"}
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>
        </div>
    );
}
