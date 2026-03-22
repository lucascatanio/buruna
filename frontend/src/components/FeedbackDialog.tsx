import {useState} from "react";
import {Dialog} from "radix-ui";
import {Button} from "@/components/ui/button";
import {Textarea} from "@/components/ui/textarea";
import {MessageSquare, X} from "lucide-react";
import api from "@/lib/axios";
import {toast} from "sonner";

const MAX_LENGTH = 2000;

export function FeedbackButton() {
    const [open, setOpen] = useState(false);
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        if (!message.trim()) return;

        setLoading(true);
        try {
            await api.post("/feedback", {message});
            toast.success("Feedback enviado! Obrigado pela sua mensagem.");
            setMessage("");
            setOpen(false);
        } catch (err: unknown) {
            const status = (err as {response?: {status?: number}})?.response?.status;
            if (status === 429) {
                toast.error("Limite atingido. Tente novamente em 1 hora.");
            } else {
                toast.error("Não foi possível enviar o feedback. Tente novamente.");
            }
        } finally {
            setLoading(false);
        }
    }

    return (
        <Dialog.Root open={open} onOpenChange={setOpen}>
            <Dialog.Trigger asChild>
                <button
                    aria-label="Enviar feedback"
                    className="fixed bottom-24 left-4 md:bottom-6 md:left-6 z-50 flex items-center justify-center size-14 rounded-full bg-primary text-primary-foreground shadow-lg hover:bg-primary/90 transition-colors"
                >
                    <MessageSquare className="size-6"/>
                </button>
            </Dialog.Trigger>

            <Dialog.Portal>
                <Dialog.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"/>
                <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border bg-background p-6 shadow-lg data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95">
                    <div className="flex items-center justify-between mb-4">
                        <Dialog.Title className="text-base font-semibold">
                            Enviar feedback
                        </Dialog.Title>
                        <Dialog.Close asChild>
                            <Button variant="ghost" size="icon" aria-label="Fechar">
                                <X className="size-4"/>
                            </Button>
                        </Dialog.Close>
                    </div>

                    <Dialog.Description className="text-sm text-muted-foreground mb-4">
                        Sugestões, bugs ou qualquer coisa que queira compartilhar.
                    </Dialog.Description>

                    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                        <div className="relative">
                            <Textarea
                                placeholder="Escreva sua mensagem..."
                                value={message}
                                onChange={(e) => setMessage(e.target.value)}
                                maxLength={MAX_LENGTH}
                                rows={5}
                                disabled={loading}
                                className="resize-none pr-2"
                            />
                            <span className="absolute bottom-2 right-3 text-xs text-muted-foreground select-none">
                                {message.length}/{MAX_LENGTH}
                            </span>
                        </div>

                        <Button
                            type="submit"
                            disabled={loading || !message.trim()}
                            className="self-end"
                        >
                            {loading ? "Enviando..." : "Enviar"}
                        </Button>
                    </form>
                </Dialog.Content>
            </Dialog.Portal>
        </Dialog.Root>
    );
}
