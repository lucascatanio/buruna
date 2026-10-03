import {useState, useEffect} from "react";
import {useSearchParams, useNavigate, Link} from "react-router-dom";
import {toast} from "sonner";
import {getResetInfo, resetPassword} from "@/api/identityApi";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {PasswordInput} from "@/components/PasswordInput";
import {AuthLayout} from "@/components/AuthLayout";

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
        getResetInfo(token)
            .then((data) => setTotpRequired(data.totpRequired))
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
            await resetPassword({
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
            <AuthLayout title="Link inválido" description="O link de recuperação é inválido ou está incompleto.">
                <Button variant="outline" className="w-full h-12" asChild>
                    <Link to="/forgot-password">Solicitar novo link</Link>
                </Button>
            </AuthLayout>
        );
    }

    if (checkingToken) {
        return <AuthLayout title="Redefinir senha" description="Verificando link…"/>;
    }

    return (
        <AuthLayout title="Redefinir senha" description="Digite sua nova senha.">
            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
                <div className="flex flex-col gap-2">
                    <Label htmlFor="newPassword">Nova senha</Label>
                    <PasswordInput
                        id="newPassword"
                        placeholder="Mín. 8 caracteres"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        required
                        autoFocus
                        autoComplete="new-password"
                    />
                </div>
                {totpRequired && (
                    <div className="flex flex-col gap-2">
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
                            className="font-mono tracking-[0.3em]"
                        />
                        <p className="m-0 text-xs text-muted-foreground">
                            Sua conta possui 2FA ativado. Digite o código do app autenticador.
                        </p>
                    </div>
                )}
                <Button type="submit" className="w-full mt-2" disabled={loading}>
                    {loading ? "Redefinindo…" : "Redefinir senha"}
                </Button>
            </form>
            <Link to="/login" className="self-start text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground">
                Voltar ao login
            </Link>
        </AuthLayout>
    );
}
