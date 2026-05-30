import {useEffect, useRef, useState} from "react";
import {useNavigate, Link} from "react-router-dom";
import {toast} from "sonner";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from "@/components/ui/card";

declare global {
    interface Window {
        hcaptcha: {
            render: (container: string, options: { sitekey: string; callback: (token: string) => void; "expired-callback": () => void }) => string;
            reset: (widgetId?: string) => void;
        };
    }
}

const HCAPTCHA_SITE_KEY = import.meta.env.VITE_HCAPTCHA_SITE_KEY;
const HAS_CAPTCHA = Boolean(HCAPTCHA_SITE_KEY);

export function RegisterPage() {
    const navigate = useNavigate();
    const widgetId = useRef<string | undefined>(undefined);

    const [form, setForm] = useState({
        email: "",
        username: "",
        password: "",
        presentationMessage: "",
    });
    const [captchaToken, setCaptchaToken] = useState(HAS_CAPTCHA ? "" : "dev-skip-captcha");
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!HAS_CAPTCHA) return;
        const script = document.createElement("script");
        script.src = "https://js.hcaptcha.com/1/api.js";
        script.async = true;
        script.defer = true;
        script.onload = () => {
            widgetId.current = window.hcaptcha.render("hcaptcha-container", {
                sitekey: HCAPTCHA_SITE_KEY,
                callback: (token: string) => setCaptchaToken(token),
                "expired-callback": () => setCaptchaToken(""),
            });
        };
        document.body.appendChild(script);
        return () => {
            document.body.removeChild(script);
        };
    }, []);

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
            await api.post("/auth/register", {...form, captchaToken});
            toast.success("Solicitação enviada! Aguarde a aprovação do admin.");
            navigate("/login");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar solicitação");
            window.hcaptcha?.reset(widgetId.current);
            setCaptchaToken(HAS_CAPTCHA ? "" : "dev-skip-captcha");
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
                        {HAS_CAPTCHA && <div id="hcaptcha-container" className="flex justify-center" />}
                        <Button type="submit" className="w-full" disabled={loading || (HAS_CAPTCHA && !captchaToken)}>
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