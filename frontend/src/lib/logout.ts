import {logout as logoutRequest} from "@/api/identityApi";
import {useAuthStore} from "@/store/authStore";

export async function performLogout(): Promise<void> {
    try {
        // O cookie httpOnly buruna_refresh vai junto (withCredentials no axios) —
        // nada para o frontend ler ou repassar (ADR-41).
        await logoutRequest();
    } catch {
    }

    useAuthStore.getState().clearAuth();
}
