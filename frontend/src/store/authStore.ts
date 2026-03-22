import {create} from "zustand";
import {persist} from "zustand/middleware";
import {clearSignedUrlCache} from "@/lib/signedUrlCache";

export interface AuthUser {
    id: string;
    role: "READER" | "COLLABORATOR" | "ADMIN";
}

interface AuthState {
    accessToken: string | null;
    refreshToken: string | null;
    user: AuthUser | null;
    setTokens: (accessToken: string, refreshToken: string) => void;
    clearAuth: () => void;
}

function decodeUser(token: string): AuthUser | null {
    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        return {id: payload.sub, role: payload.role};
    } catch {
        return null;
    }
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            accessToken: null,
            refreshToken: null,
            user: null,
            setTokens: (accessToken, refreshToken) =>
                set({accessToken, refreshToken, user: decodeUser(accessToken)}),
            clearAuth: () => {
                clearSignedUrlCache();
                set({accessToken: null, refreshToken: null, user: null});
            },
        }),
        {name: "buruna-auth"}
    )
);
