import {useState, useEffect} from "react";
import {useNavigate} from "react-router-dom";
import {QRCodeSVG} from "qrcode.react";
import {toast} from "sonner";
import {deleteAccount, disable2FA, get2FAStatus, setup2FA, verify2FA} from "@/api/identityApi";
import {useAuthStore} from "@/store/authStore";
import type {TotpSetupResponse} from "@/types/identity";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";
import {PageHeader} from "@/components/PageHeader";
import {ShieldCheck, ShieldOff, Trash2} from "lucide-react";

export function SecuritySettingsPage() {
    const [totpEnabled, setTotpEnabled] = useState(false);
    const [setupData, setSetupData] = useState<TotpSetupResponse | null>(null);
    const [code, setCode] = useState("");
    const [disableCode, setDisableCode] = useState("");
    const [loading, setLoading] = useState(false);
    const [showDisable, setShowDisable] = useState(false);
    const [showDelete, setShowDelete] = useState(false);
    const [deletePassword, setDeletePassword] = useState("");
    const [deleteCode, setDeleteCode] = useState("");
    const [deleting, setDeleting] = useState(false);
    const clearAuth = useAuthStore((s) => s.clearAuth);
    const navigate = useNavigate();

    useEffect(() => {
        get2FAStatus().then((data) => {
            setTotpEnabled(data.totpEnabled);
        }).catch(() => {});
    }, []);

    async function handleSetup() {
        setLoading(true);
        try {
            const data = await setup2FA();
            setSetupData(data);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao gerar QR code");
        } finally {
            setLoading(false);
        }
    }

    async function handleVerify(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            await verify2FA(code);
            setTotpEnabled(true);
            setSetupData(null);
            setCode("");
            toast.success("2FA ativado com sucesso!");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Código inválido");
        } finally {
            setLoading(false);
        }
    }

    async function handleDisable(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            await disable2FA(disableCode);
            setTotpEnabled(false);
            setDisableCode("");
            setShowDisable(false);
            toast.success("2FA desativado");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Código inválido");
        } finally {
            setLoading(false);
        }
    }

    function cancelDelete() {
        setShowDelete(false);
        setDeletePassword("");
        setDeleteCode("");
    }

    async function handleDelete(e: React.FormEvent) {
        e.preventDefault();
        setDeleting(true);
        try {
            await deleteAccount(deletePassword, totpEnabled ? deleteCode : undefined);
            // o backend já apagou os tokens e limpou o cookie de refresh
            clearAuth();
            toast.success("Conta excluída");
            navigate("/login", {replace: true});
        } catch (err: any) {
            const status = err.response?.status;
            toast.error(status === 429
                ? "Muitas tentativas. Tente novamente mais tarde."
                : err.response?.data?.message ?? "Não foi possível excluir a conta");
            setDeleting(false);
        }
    }

    return (
        <div className="max-w-2xl mx-auto p-6 space-y-6">
            <PageHeader title="Segurança" description="Autenticação em dois fatores e acesso à sua conta."/>

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        {totpEnabled ? <ShieldCheck className="w-5 h-5 text-foreground"/> : <ShieldOff className="w-5 h-5 text-muted-foreground"/>}
                        Autenticação em dois fatores (2FA)
                    </CardTitle>
                    <CardDescription>
                        {totpEnabled
                            ? "2FA está ativado. Sua conta está protegida com autenticação TOTP."
                            : "Adicione uma camada extra de segurança usando um app autenticador."}
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                    {!totpEnabled && !setupData && (
                        <Button onClick={handleSetup} disabled={loading}>
                            {loading ? "Gerando…" : "Ativar 2FA"}
                        </Button>
                    )}

                    {setupData && (
                        <div className="space-y-4">
                            <div className="space-y-2">
                                <p className="text-sm text-muted-foreground">
                                    Escaneie o QR code abaixo com seu app autenticador (Google Authenticator, Authy, etc.):
                                </p>
                                <div className="flex justify-center p-4 bg-white rounded-lg">
                                    {/* gerado no navegador: a URI carrega o segredo TOTP e não pode sair daqui */}
                                    <QRCodeSVG value={setupData.qrUri} size={200} title="QR Code 2FA"/>
                                </div>
                                <details className="text-xs text-muted-foreground">
                                    <summary className="cursor-pointer">Não consegue escanear? Use a chave manual</summary>
                                    <code className="mt-1 block break-all bg-muted p-2 rounded text-xs">{setupData.secret}</code>
                                </details>
                            </div>
                            <form onSubmit={handleVerify} className="space-y-3">
                                <div className="space-y-2">
                                    <Label htmlFor="verify-code">Código de verificação</Label>
                                    <Input
                                        id="verify-code"
                                        type="text"
                                        inputMode="numeric"
                                        pattern="[0-9]{6}"
                                        maxLength={6}
                                        placeholder="000000"
                                        value={code}
                                        onChange={(e) => setCode(e.target.value)}
                                        required
                                        autoComplete="one-time-code"
                                    />
                                </div>
                                <div className="flex gap-2">
                                    <Button type="submit" disabled={loading || code.length !== 6}>
                                        {loading ? "Verificando…" : "Confirmar ativação"}
                                    </Button>
                                    <Button type="button" variant="ghost" onClick={() => { setSetupData(null); setCode(""); }}>
                                        Cancelar
                                    </Button>
                                </div>
                            </form>
                        </div>
                    )}

                    {totpEnabled && !showDisable && (
                        <Button variant="destructive" onClick={() => setShowDisable(true)}>
                            Desativar 2FA
                        </Button>
                    )}

                    {showDisable && (
                        <form onSubmit={handleDisable} className="space-y-3">
                            <div className="space-y-2">
                                <Label htmlFor="disable-code">Código TOTP para confirmar</Label>
                                <Input
                                    id="disable-code"
                                    type="text"
                                    inputMode="numeric"
                                    pattern="[0-9]{6}"
                                    maxLength={6}
                                    placeholder="000000"
                                    value={disableCode}
                                    onChange={(e) => setDisableCode(e.target.value)}
                                    required
                                    autoFocus
                                    autoComplete="one-time-code"
                                />
                            </div>
                            <div className="flex gap-2">
                                <Button type="submit" variant="destructive" disabled={loading || disableCode.length !== 6}>
                                    {loading ? "Desativando…" : "Confirmar desativação"}
                                </Button>
                                <Button type="button" variant="ghost" onClick={() => { setShowDisable(false); setDisableCode(""); }}>
                                    Cancelar
                                </Button>
                            </div>
                        </form>
                    )}
                </CardContent>
            </Card>

            <Card className="border-destructive/50">
                <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-destructive">
                        <Trash2 className="w-5 h-5"/>
                        Excluir conta
                    </CardTitle>
                    <CardDescription>
                        Remove sua conta de forma permanente. Esta ação não pode ser desfeita.
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                    {!showDelete && (
                        <Button variant="destructive" onClick={() => setShowDelete(true)}>
                            Excluir minha conta
                        </Button>
                    )}

                    {showDelete && (
                        <form onSubmit={handleDelete} className="space-y-4">
                            <div className="text-sm space-y-2 rounded-md border border-destructive/40 bg-destructive/5 p-3">
                                <p className="font-medium text-foreground">O que acontece:</p>
                                <ul className="list-disc pl-5 space-y-1 text-muted-foreground">
                                    <li>Sua coleção privada, com volumes e arquivos, é apagada.</li>
                                    <li>Seu e-mail, nome de usuário, avatar e senha são removidos, e você não consegue mais entrar.</li>
                                    <li>Mangás e volumes que você publicou na biblioteca pública continuam disponíveis, sem o seu nome.</li>
                                </ul>
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="delete-password">Senha atual</Label>
                                <Input
                                    id="delete-password"
                                    type="password"
                                    value={deletePassword}
                                    onChange={(e) => setDeletePassword(e.target.value)}
                                    required
                                    autoFocus
                                    autoComplete="current-password"
                                />
                            </div>
                            {totpEnabled && (
                                <div className="space-y-2">
                                    <Label htmlFor="delete-code">Código do app autenticador</Label>
                                    <Input
                                        id="delete-code"
                                        type="text"
                                        inputMode="numeric"
                                        pattern="[0-9]{6}"
                                        maxLength={6}
                                        placeholder="000000"
                                        value={deleteCode}
                                        onChange={(e) => setDeleteCode(e.target.value)}
                                        required
                                        autoComplete="one-time-code"
                                    />
                                </div>
                            )}
                            <div className="flex gap-2">
                                <Button
                                    type="submit"
                                    variant="destructive"
                                    disabled={deleting || !deletePassword || (totpEnabled && deleteCode.length !== 6)}
                                >
                                    {deleting ? "Excluindo…" : "Excluir conta definitivamente"}
                                </Button>
                                <Button type="button" variant="ghost" onClick={cancelDelete} disabled={deleting}>
                                    Cancelar
                                </Button>
                            </div>
                        </form>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
