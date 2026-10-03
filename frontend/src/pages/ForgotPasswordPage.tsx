import {useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "sonner";
import {forgotPassword} from "@/api/identityApi";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {AuthLayout} from "@/components/AuthLayout";

export function ForgotPasswordPage() {
    const [email, setEmail] = useState("");
    const [loading, setLoading] = useState(false);
    const [sent, setSent] = useState(false);

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            await forgotPassword(email);
            setSent(true);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar e-mail");
        } finally {
            setLoading(false);
        }
    }

    if (sent) {
        return (
            <AuthLayout
                title="E-mail enviado"
                description="Se o e-mail estiver cadastrado, enviaremos instruções de recuperação."
            >
                <Button variant="outline" className="w-full h-12" asChild>
                    <Link to="/login">Voltar ao login</Link>
                </Button>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout title="Esqueci minha senha" description="Digite seu e-mail para receber instruções de recuperação.">
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
                <Button type="submit" className="w-full mt-2" disabled={loading}>
                    {loading ? "Enviando…" : "Enviar"}
                </Button>
            </form>
            <Link to="/login" className="self-start text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground">
                Voltar ao login
            </Link>
        </AuthLayout>
    );
}
