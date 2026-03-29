import {useState, useEffect} from "react";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";
import {ShieldCheck, ShieldOff} from "lucide-react";

export function SecuritySettingsPage() {
    const [totpEnabled, setTotpEnabled] = useState(false);
    const [setupData, setSetupData] = useState<{ secret: string; qrUri: string } | null>(null);
    const [code, setCode] = useState("");
    const [disableCode, setDisableCode] = useState("");
    const [loading, setLoading] = useState(false);
    const [showDisable, setShowDisable] = useState(false);

    useEffect(() => {
        api.get("/auth/2fa/status").then(({data}) => {
            setTotpEnabled(data.totpEnabled);
        }).catch(() => {});
    }, []);

    async function handleSetup() {
        setLoading(true);
        try {
            const {data} = await api.post("/auth/2fa/setup");
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
            await api.post("/auth/2fa/verify", {code});
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
            await api.post("/auth/2fa/disable", {code: disableCode});
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

    return (
        <div className="max-w-2xl mx-auto p-6 space-y-6">
            <h1 className="text-2xl font-semibold">Segurança</h1>

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        {totpEnabled ? <ShieldCheck className="w-5 h-5 text-green-500"/> : <ShieldOff className="w-5 h-5 text-muted-foreground"/>}
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
                                    <img
                                        src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(setupData.qrUri)}`}
                                        alt="QR Code 2FA"
                                        width={200}
                                        height={200}
                                    />
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
        </div>
    );
}
