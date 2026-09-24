import {create} from "zustand";
import {clearSignedUrlCache} from "@/lib/signedUrlCache";

export interface AuthUser {
    id: string;
    role: "READER" | "COLLABORATOR" | "ADMIN";
}

interface AuthState {
    accessToken: string | null;
    user: AuthUser | null;
    /** true depois que o bootstrap (POST /auth/refresh via cookie) resolveu, com ou sem sessão. */
    initialized: boolean;
    setTokens: (accessToken: string) => void;
    clearAuth: () => void;
    setInitialized: () => void;
}

function decodeUser(token: string): AuthUser | null {
    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        return {id: payload.sub, role: payload.role};
    } catch {
        return null;
    }
}

// ADR-41: refresh token vive só no cookie httpOnly buruna_refresh (invisível ao JS);
// o access token fica só em memória, sem persist — um XSS não consegue mais lê-los
// do localStorage.
export const useAuthStore = create<AuthState>()((set) => ({
    accessToken: null,
    user: null,
    initialized: false,
    setTokens: (accessToken) => set({accessToken, user: decodeUser(accessToken)}),
    clearAuth: () => {
        clearSignedUrlCache();
        set({accessToken: null, user: null});
    },
    setInitialized: () => set({initialized: true}),
}));
