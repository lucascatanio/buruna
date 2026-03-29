import {useState, useEffect} from "react";
import {useSearchParams, useNavigate, Link} from "react-router-dom";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";

export function ResetPasswordPage() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const token = searchParams.get("token") ?? "";

    const [newPassword, setNewPassword] = useState("");
    const [totpCode, setTotpCode] = useState("");
    const [totpRequired, setTotpRequired] = useState(false);
    const [loading, setLoading] = useState(false);
    const [checkingToken, setCheckingToken] = useState(true);

    useEffect(() => {
        if (!token) {
            setCheckingToken(false);
            return;
        }
        api.get("/auth/password/reset-info", {params: {token}})
            .then(({data}) => setTotpRequired(data.totpRequired))
            .catch(() => {})
            .finally(() => setCheckingToken(false));
    }, [token]);

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        if (newPassword.length < 8) {
            toast.error("A senha deve ter no mínimo 8 caracteres");
            return;
        }
        setLoading(true);
        try {
            await api.post("/auth/password/reset", {
                token,
                newPassword,
                totpCode: totpRequired ? totpCode : undefined,
            });
            toast.success("Senha alterada com sucesso! Faça login com a nova senha.");
            navigate("/login");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao resetar senha");
        } finally {
            setLoading(false);
        }
    }

    if (!token) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-background px-4">
                <Card className="w-full max-w-md">
                    <CardHeader className="text-center">
                        <CardTitle>Link inválido</CardTitle>
                        <CardDescription>O link de recuperação é inválido ou está incompleto.</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <Link to="/forgot-password">
                            <Button variant="outline" className="w-full">Solicitar novo link</Button>
                        </Link>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (checkingToken) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-background px-4">
                <p className="text-muted-foreground">Verificando link…</p>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-background px-4">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <CardTitle className="text-2xl">Redefinir senha</CardTitle>
                    <CardDescription>Digite sua nova senha</CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="space-y-2">
                            <Label htmlFor="newPassword">Nova senha</Label>
                            <Input
                                id="newPassword"
                                type="password"
                                placeholder="Mín. 8 caracteres"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                required
                                autoFocus
                            />
                        </div>
                        {totpRequired && (
                            <div className="space-y-2">
                                <Label htmlFor="totpCode">Código 2FA</Label>
                                <Input
                                    id="totpCode"
                                    type="text"
                                    inputMode="numeric"
                                    pattern="[0-9]{6}"
                                    maxLength={6}
                                    placeholder="000000"
                                    value={totpCode}
                                    onChange={(e) => setTotpCode(e.target.value)}
                                    required
                                    autoComplete="one-time-code"
                                />
                                <p className="text-xs text-muted-foreground">
                                    Sua conta possui 2FA ativado. Digite o código do app autenticador.
                                </p>
                            </div>
                        )}
                        <Button type="submit" className="w-full" disabled={loading}>
                            {loading ? "Redefinindo…" : "Redefinir senha"}
                        </Button>
                    </form>
                    <p className="text-center text-sm text-muted-foreground mt-4">
                        <Link to="/login" className="underline underline-offset-4 hover:text-primary">
                            Voltar ao login
                        </Link>
                    </p>
                </CardContent>
            </Card>
        </div>
    );
}
