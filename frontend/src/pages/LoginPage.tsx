import {useState} from "react";
import {useNavigate, Link} from "react-router-dom";
import {toast} from "sonner";
import api from "@/lib/axios";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";

declare const __APP_VERSION__: string

export function LoginPage() {
    const navigate = useNavigate();
    const setTokens = useAuthStore((s) => s.setTokens);

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [loading, setLoading] = useState(false);

    const [requires2FA, setRequires2FA] = useState(false);
    const [tempToken, setTempToken] = useState("");
    const [totpCode, setTotpCode] = useState("");

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            const {data} = await api.post("/auth/login", {email, password});
            if (data.requires2FA) {
                setRequires2FA(true);
                setTempToken(data.tempToken);
            } else {
                setTokens(data.accessToken, data.refreshToken);
                navigate("/");
            }
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Credenciais inválidas");
        } finally {
            setLoading(false);
        }
    }

    async function handle2FA(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            const {data} = await api.post("/auth/2fa/authenticate", {tempToken, totpCode});
            setTokens(data.accessToken, data.refreshToken);
            navigate("/");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Código inválido");
        } finally {
            setLoading(false);
        }
    }

    if (requires2FA) {
        return (
            <div className="min-h-screen flex flex-col items-center justify-center bg-background px-4 gap-3">
                <Card className="w-full max-w-md">
                    <CardHeader className="text-center">
                        <CardTitle className="text-2xl">Verificação 2FA</CardTitle>
                        <CardDescription>Digite o código do seu app autenticador</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form onSubmit={handle2FA} className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="totpCode">Código TOTP</Label>
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
                                    autoFocus
                                    autoComplete="one-time-code"
                                />
                            </div>
                            <Button type="submit" className="w-full" disabled={loading || totpCode.length !== 6}>
                                {loading ? "Verificando…" : "Verificar"}
                            </Button>
                        </form>
                        <p className="text-center text-sm text-muted-foreground mt-4">
                            <button
                                type="button"
                                className="underline underline-offset-4 hover:text-primary"
                                onClick={() => {
                                    setRequires2FA(false);
                                    setTempToken("");
                                    setTotpCode("");
                                }}
                            >
                                Voltar ao login
                            </button>
                        </p>
                    </CardContent>
                </Card>
                <p className="text-xs text-muted-foreground/50 text-center">
                    v{__APP_VERSION__}
                </p>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex flex-col items-center justify-center bg-background px-4 gap-3">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <CardTitle className="text-2xl">Burūna</CardTitle>
                    <CardDescription>Entre na sua conta</CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="space-y-2">
                            <Label htmlFor="email">E-mail</Label>
                            <Input
                                id="email"
                                type="email"
                                placeholder="voce@exemplo.com"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                                autoFocus
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="password">Senha</Label>
                            <Input
                                id="password"
                                type="password"
                                placeholder="••••••••"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                            />
                        </div>
                        <Button type="submit" className="w-full" disabled={loading}>
                            {loading ? "Entrando…" : "Entrar"}
                        </Button>
                    </form>
                    <div className="flex justify-between items-center mt-4">
                        <Link to="/forgot-password" className="text-sm text-muted-foreground underline underline-offset-4 hover:text-primary">
                            Esqueci minha senha
                        </Link>
                        <Link to="/register" className="text-sm text-muted-foreground underline underline-offset-4 hover:text-primary">
                            Solicitar acesso
                        </Link>
                    </div>
                </CardContent>
            </Card>
            <p className="text-xs text-muted-foreground/50 text-center">
                v{__APP_VERSION__}
            </p>
        </div>
    );
}
