import {useState} from "react";
import {useNavigate, Link} from "react-router-dom";
import {toast} from "sonner";
import {authenticate2FA, login} from "@/api/identityApi";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {AuthLayout} from "@/components/AuthLayout";
import {PasswordInput} from "@/components/PasswordInput";

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
            const data = await login(email, password);
            if (data.requires2FA) {
                setRequires2FA(true);
                setTempToken(data.tempToken!);
            } else {
                setTokens(data.accessToken!);
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
            const data = await authenticate2FA(tempToken, totpCode);
            setTokens(data.accessToken!);
            navigate("/");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Código inválido");
        } finally {
            setLoading(false);
        }
    }

    if (requires2FA) {
        return (
            <AuthLayout title="Verificação 2FA" description="Digite o código do seu app autenticador.">
                <form onSubmit={handle2FA} className="flex flex-col gap-5">
                    <div className="flex flex-col gap-2">
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
                            className="font-mono tracking-[0.3em]"
                        />
                    </div>
                    <Button type="submit" className="w-full mt-2" disabled={loading || totpCode.length !== 6}>
                        {loading ? "Verificando…" : "Verificar"}
                    </Button>
                </form>
                <button
                    type="button"
                    className="self-start text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
                    onClick={() => {
                        setRequires2FA(false);
                        setTempToken("");
                        setTotpCode("");
                    }}
                >
                    Voltar ao login
                </button>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout title="Entrar" description="Acesse sua biblioteca de mangás.">
            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
                <div className="flex flex-col gap-2">
                    <Label htmlFor="email">E-mail</Label>
                    <Input
                        id="email"
                        type="email"
                        placeholder="voce@exemplo.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        autoFocus
                        autoComplete="email"
                    />
                </div>
                <div className="flex flex-col gap-2">
                    <div className="flex items-baseline justify-between gap-3">
                        <Label htmlFor="password">Senha</Label>
                        <Link to="/forgot-password" className="text-[13px] text-muted-foreground hover:text-foreground hover:underline underline-offset-4">
                            Esqueci minha senha
                        </Link>
                    </div>
                    <PasswordInput
                        id="password"
                        placeholder="••••••••"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        autoComplete="current-password"
                    />
                </div>
                <Button type="submit" className="w-full mt-2" disabled={loading}>
                    {loading ? "Entrando…" : "Entrar"}
                </Button>
            </form>
            <div className="flex flex-col gap-6">
                <div className="h-px bg-border"/>
                <p className="m-0 text-sm text-muted-foreground">
                    Ainda não tem acesso?{" "}
                    <Link to="/register" className="font-medium text-foreground underline-offset-4 hover:underline">
                        Solicitar acesso
                    </Link>
                </p>
            </div>
        </AuthLayout>
    );
}
