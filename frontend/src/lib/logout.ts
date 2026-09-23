import {logout as logoutRequest} from "@/api/identityApi";
import {useAuthStore} from "@/store/authStore";

export async function performLogout(): Promise<void> {
    const {refreshToken, clearAuth} = useAuthStore.getState();

    if (refreshToken) {
        try {
            await logoutRequest(refreshToken);
        } catch {
        }
    }

    clearAuth();
}
