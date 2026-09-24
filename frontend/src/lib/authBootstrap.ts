import axios from "axios";
import {useAuthStore} from "@/store/authStore";

/** Chave do Zustand persist usado antes do ADR-41 (tokens em localStorage). */
const LEGACY_STORAGE_KEY = "buruna-auth";

let started = false;

/**
 * Roda uma vez na inicialização do app: tenta trocar o cookie httpOnly buruna_refresh
 * por um accessToken (sem exigir que o usuário refaça login a cada F5). `started`
 * evita uma segunda chamada em paralelo — o StrictMode do React 18 invoca efeitos
 * duas vezes em dev, e duas chamadas concorrentes de refresh rotacionariam o cookie
 * duas vezes, derrubando uma delas por corrida.
 */
export async function bootstrapAuth(): Promise<void> {
    if (started) return;
    started = true;

    try {
        localStorage.removeItem(LEGACY_STORAGE_KEY);
    } catch {
        // localStorage indisponível (ex.: modo privado) — segue sem limpar.
    }

    const {setTokens, clearAuth, setInitialized} = useAuthStore.getState();

    try {
        const {data} = await axios.post("/api/auth/refresh", undefined, {withCredentials: true});
        setTokens(data.accessToken);
    } catch {
        clearAuth();
    } finally {
        setInitialized();
    }
}
