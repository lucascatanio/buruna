import {useState} from "react";
import {useNavigate, Link} from "react-router-dom";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";

export function RegisterPage() {
    const navigate = useNavigate();

    const [form, setForm] = useState({
        email: "",
        username: "",
        password: "",
        presentationMessage: "",
    });
    const [loading, setLoading] = useState(false);

    function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
        setForm((prev) => ({...prev, [e.target.name]: e.target.value}));
    }

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        if (form.password.length < 8) {
            toast.error("A senha deve ter no mínimo 8 caracteres");
            return;
        }
        setLoading(true);
        try {
            await api.post("/auth/register", form);
            toast.success("Solicitação enviada! Aguarde a aprovação do admin.");
            navigate("/login");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar solicitação");
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-background px-4">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <CardTitle className="text-2xl">Criar conta</CardTitle>
                    <CardDescription>Sua solicitação será revisada pelo admin</CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="space-y-2">
                            <Label htmlFor="email">E-mail</Label>
                            <Input
                                id="email"
                                name="email"
                                type="email"
                                placeholder="voce@exemplo.com"
                                value={form.email}
                                onChange={handleChange}
                                required
                                autoFocus
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="username">Nome de usuário</Label>
                            <Input
                                id="username"
                                name="username"
                                placeholder="seunome"
                                minLength={3}
                                maxLength={50}
                                value={form.username}
                                onChange={handleChange}
                                required
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="password">Senha</Label>
                            <Input
                                id="password"
                                name="password"
                                type="password"
                                placeholder="Mín. 8 caracteres"
                                value={form.password}
                                onChange={handleChange}
                                required
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="presentationMessage">Por que quer entrar?</Label>
                            <Input
                                id="presentationMessage"
                                name="presentationMessage"
                                placeholder="Conte um pouco sobre você"
                                value={form.presentationMessage}
                                onChange={handleChange}
                                required
                            />
                        </div>
                        <Button type="submit" className="w-full" disabled={loading}>
                            {loading ? "Enviando…" : "Solicitar acesso"}
                        </Button>
                    </form>
                    <p className="text-center text-sm text-muted-foreground mt-4">
                        Já tem uma conta?{" "}
                        <Link to="/login" className="underline underline-offset-4 hover:text-primary">
                            Entrar
                        </Link>
                    </p>
                </CardContent>
            </Card>
        </div>
    );
}