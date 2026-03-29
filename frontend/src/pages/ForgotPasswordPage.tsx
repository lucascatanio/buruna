import {useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";

export function ForgotPasswordPage() {
    const [email, setEmail] = useState("");
    const [loading, setLoading] = useState(false);
    const [sent, setSent] = useState(false);

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            await api.post("/auth/password/forgot", {email});
            setSent(true);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar e-mail");
        } finally {
            setLoading(false);
        }
    }

    if (sent) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-background px-4">
                <Card className="w-full max-w-md">
                    <CardHeader className="text-center">
                        <CardTitle className="text-2xl">E-mail enviado</CardTitle>
                        <CardDescription>
                            Se o e-mail estiver cadastrado, enviaremos instruções de recuperação.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <Link to="/login">
                            <Button variant="outline" className="w-full">
                                Voltar ao login
                            </Button>
                        </Link>
                    </CardContent>
                </Card>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-background px-4">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <CardTitle className="text-2xl">Esqueci minha senha</CardTitle>
                    <CardDescription>Digite seu e-mail para receber instruções de recuperação</CardDescription>
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
                        <Button type="submit" className="w-full" disabled={loading}>
                            {loading ? "Enviando…" : "Enviar"}
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
