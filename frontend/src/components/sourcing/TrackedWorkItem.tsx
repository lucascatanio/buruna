import {useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "sonner";
import {AlertCircle, ExternalLink, Loader2, Pause, Play, RefreshCw} from "lucide-react";
import {
    checkTrackedWork,
    ignoreGroup,
    listSourceAlerts,
    listTrackedGroups,
    pauseTrackedWork,
    resumeTrackedWork,
    runSourceChapterAction,
    stopIgnoringGroup,
    type SourceChapterAction,
} from "@/api/sourcingApi";
import type {SourceAlert, SourceAlertType, TrackedGroup, TrackedWork} from "@/types/sourcing";
import {Button} from "@/components/ui/button";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";

const ALERT_LABELS: Record<SourceAlertType, string> = {
    FAILED: "Falhou ao baixar",
    REMOVED_AT_SOURCE: "Removido no MangaDex (continua no ar aqui)",
    PAGES_CHANGED: "Páginas trocadas no MangaDex",
    PREFERRED_AVAILABLE: "Versão do grupo preferido disponível",
};

const ALERT_ACTIONS: Partial<Record<SourceAlertType, { action: SourceChapterAction; label: string }>> = {
    FAILED: {action: "retry", label: "Tentar de novo"},
    PAGES_CHANGED: {action: "redownload", label: "Baixar de novo"},
    PREFERRED_AVAILABLE: {action: "use", label: "Usar esta versão"},
};

function plural(count: number, one: string, many: string): string {
    return `${count} ${count === 1 ? one : many}`;
}

function formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleString("pt-BR", {dateStyle: "short", timeStyle: "short"}) : "nunca";
}

function chapterName(alert: SourceAlert): string {
    if (alert.number) return `Cap. ${alert.number}`;
    return alert.title ?? "Oneshot";
}

interface TrackedWorkItemProps {
    tracked: TrackedWork;
    /** Recarrega a lista depois de uma ação. */
    onChanged: () => void;
}

/** Uma obra acompanhada: andamento, ações (verificar, pausar, retomar), avisos e grupos. */
export function TrackedWorkItem({tracked: t, onChanged}: TrackedWorkItemProps) {
    const [panel, setPanel] = useState<"alerts" | "groups" | null>(null);
    const [alerts, setAlerts] = useState<SourceAlert[] | null>(null);
    const [groups, setGroups] = useState<TrackedGroup[] | null>(null);
    const [busy, setBusy] = useState<string | null>(null);

    const counts = t.chapterCounts;
    const pending = counts.PENDING ?? 0;
    const versions = Object.values(counts).reduce((sum, n) => sum + (n ?? 0), 0);
    const paused = t.status === "PAUSED";

    async function run(key: string, action: () => Promise<void>, success: string, reload?: () => Promise<void>) {
        setBusy(key);
        try {
            await action();
            toast.success(success);
            onChanged();
            await reload?.();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não deu certo. Tente de novo."));
        } finally {
            setBusy(null);
        }
    }

    async function loadAlerts() {
        setAlerts(await listSourceAlerts(t.id));
    }

    async function loadGroups() {
        setGroups(await listTrackedGroups(t.id));
    }

    async function togglePanel(next: "alerts" | "groups") {
        if (panel === next) {
            setPanel(null);
            return;
        }
        setPanel(next);
        try {
            await (next === "alerts" ? loadAlerts() : loadGroups());
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível carregar."));
        }
    }

    function handleIgnore(group: TrackedGroup) {
        if (!confirm(`Ignorar ${group.name}? Os capítulos que vieram desse grupo são apagados, e entra a versão de outro grupo quando houver.`)) return;
        void run(`group-${group.id}`, () => ignoreGroup(t.id, group.id, group.name), "Grupo ignorado.", loadGroups);
    }

    return (
        <li className="space-y-2 p-4">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
                <Link to={`/biblioteca/${t.workSlug}`} className="font-medium hover:underline">{t.workTitle}</Link>
                <a href={t.sourceUrl} target="_blank" rel="noopener noreferrer"
                   className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:underline">
                    MangaDex <ExternalLink className="size-3"/>
                </a>
            </div>
            <p className="text-sm text-muted-foreground">
                {t.languages.map((l) => {
                    const ignored = t.ignoredGroups.some((g) => g.id === l.preferredGroupId);
                    const group = l.preferredGroupName ? ` (${l.preferredGroupName}${ignored ? ", ignorado" : ""})` : "";
                    return languageName(l.language) + group;
                }).join(" · ")}
            </p>
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-muted-foreground">
                {paused && <span className="text-foreground">pausada</span>}
                {t.lastCheckedAt && versions === 0
                    ? <span>nenhum capítulo disponível na fonte</span>
                    : <span>{plural(counts.READY ?? 0, "baixado", "baixados")}</span>}
                {pending > 0 && (
                    <span className="flex items-center gap-1 text-foreground">
                        <Loader2 className="size-3 animate-spin"/> {pending} na fila
                    </span>
                )}
                {(counts.FAILED ?? 0) > 0 && <span className="text-destructive">{counts.FAILED} com falha</span>}
                <span>{plural(counts.IGNORED ?? 0, "versão de outro grupo", "versões de outros grupos")}</span>
                <span>verificada {formatDate(t.lastCheckedAt)}</span>
            </p>
            {(t.pausedReason || t.lastError) && (
                <p className="flex items-center gap-1 text-xs text-destructive">
                    <AlertCircle className="size-3.5 shrink-0"/> {t.pausedReason ?? t.lastError}
                </p>
            )}

            <div className="flex flex-wrap gap-2 pt-1">
                {paused ? (
                    <Button size="sm" variant="outline" disabled={busy !== null}
                            onClick={() => run("resume", () => resumeTrackedWork(t.id), "Acompanhamento retomado.")}>
                        <Play className="size-3.5"/> Retomar
                    </Button>
                ) : (
                    <>
                        <Button size="sm" variant="outline" disabled={busy !== null}
                                onClick={() => run("check", () => checkTrackedWork(t.id), "Verificação feita.")}>
                            {busy === "check" ? <Loader2 className="size-3.5 animate-spin"/> : <RefreshCw className="size-3.5"/>}
                            Verificar agora
                        </Button>
                        <Button size="sm" variant="ghost" disabled={busy !== null}
                                onClick={() => run("pause", () => pauseTrackedWork(t.id), "Acompanhamento pausado.")}>
                            <Pause className="size-3.5"/> Pausar
                        </Button>
                    </>
                )}
                <Button size="sm" variant={panel === "alerts" ? "secondary" : "ghost"} aria-expanded={panel === "alerts"}
                        onClick={() => togglePanel("alerts")}>
                    Avisos{t.alertCount > 0 && <span className="ml-1 font-mono text-shu">{t.alertCount}</span>}
                </Button>
                <Button size="sm" variant={panel === "groups" ? "secondary" : "ghost"} aria-expanded={panel === "groups"}
                        onClick={() => togglePanel("groups")}>
                    Grupos
                </Button>
            </div>

            {panel === "alerts" && (
                <div className="rounded-md border">
                    {alerts === null && <p className="p-3 text-sm text-muted-foreground">Carregando…</p>}
                    {alerts?.length === 0 && <p className="p-3 text-sm text-muted-foreground">Nenhum aviso.</p>}
                    <ul className="divide-y">
                        {alerts?.map((alert) => {
                            const action = ALERT_ACTIONS[alert.type];
                            const key = `${alert.type}-${alert.sourceChapterId}`;
                            return (
                                <li key={key} className="flex flex-wrap items-center gap-3 p-3 text-sm">
                                    <div className="min-w-0 flex-1">
                                        <p>
                                            <span className="font-medium">{chapterName(alert)}</span>
                                            <span className="text-muted-foreground"> · {languageName(alert.language)} · {alert.groupNames ?? "sem grupo"}</span>
                                        </p>
                                        <p className="text-xs text-muted-foreground">
                                            {ALERT_LABELS[alert.type]}
                                            {alert.type === "PREFERRED_AVAILABLE" && alert.detail ? `; hoje está a versão de ${alert.detail}` : ""}
                                            {alert.type === "FAILED" && alert.detail ? `: ${alert.detail}` : ""}
                                            {" · "}{formatDate(alert.detectedAt)}
                                        </p>
                                    </div>
                                    {action && (
                                        <Button size="sm" variant="outline" disabled={busy !== null}
                                                onClick={() => run(key, () => runSourceChapterAction(alert.sourceChapterId, action.action),
                                                    "Pedido feito. O download começa já.", loadAlerts)}>
                                            {busy === key && <Loader2 className="size-3.5 animate-spin"/>}
                                            {action.label}
                                        </Button>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                </div>
            )}

            {panel === "groups" && (
                <div className="rounded-md border">
                    {groups === null && <p className="p-3 text-sm text-muted-foreground">Carregando…</p>}
                    {groups?.length === 0 && <p className="p-3 text-sm text-muted-foreground">Nenhum grupo ainda.</p>}
                    <ul className="divide-y">
                        {groups?.map((group) => (
                            <li key={group.id} className="flex items-center gap-3 p-3 text-sm">
                                <span className={`min-w-0 flex-1 truncate ${group.ignored ? "text-muted-foreground line-through" : ""}`}>
                                    {group.name}
                                </span>
                                <span className="font-mono text-[11px] text-muted-foreground">
                                    {plural(group.versionCount, "versão", "versões")}
                                </span>
                                {group.ignored ? (
                                    <Button size="sm" variant="ghost" disabled={busy !== null}
                                            onClick={() => run(`group-${group.id}`, () => stopIgnoringGroup(t.id, group.id),
                                                "O grupo foi aceito de novo.", loadGroups)}>
                                        Voltar a aceitar
                                    </Button>
                                ) : (
                                    <Button size="sm" variant="ghost" className="text-destructive" disabled={busy !== null}
                                            onClick={() => handleIgnore(group)}>
                                        Ignorar
                                    </Button>
                                )}
                            </li>
                        ))}
                    </ul>
                    <p className="border-t p-3 text-xs text-muted-foreground">
                        Use "Ignorar" quando um grupo pedir para tirar as traduções dele.
                    </p>
                </div>
            )}
        </li>
    );
}
