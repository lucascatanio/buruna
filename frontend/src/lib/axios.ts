import axios from "axios";
import {useAuthStore} from "@/store/authStore";

const api = axios.create({baseURL: "/api", withCredentials: true});

api.interceptors.request.use((config) => {
    const token = useAuthStore.getState().accessToken;
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

let isRefreshing = false;
let failedQueue: Array<{
    resolve: (token: string) => void;
    reject: (err: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
    failedQueue.forEach((p) => (error ? p.reject(error) : p.resolve(token!)));
    failedQueue = [];
}

api.interceptors.response.use(
    (res) => res,
    async (error) => {
        const original = error.config;

        if (error.response?.status !== 401 || original._retry) {
            return Promise.reject(error);
        }

        if (isRefreshing) {
            return new Promise((resolve, reject) => {
                failedQueue.push({resolve, reject});
            }).then((token) => {
                original.headers.Authorization = `Bearer ${token}`;
                return api(original);
            });
        }

        original._retry = true;
        isRefreshing = true;

        const {setTokens, clearAuth} = useAuthStore.getState();

        try {
            // Sem corpo: o refresh token vai só no cookie httpOnly buruna_refresh,
            // invisível a este código (ADR-41) — withCredentials é o que o envia.
            const {data} = await axios.post("/api/auth/refresh", undefined, {withCredentials: true});
            setTokens(data.accessToken);
            processQueue(null, data.accessToken);
            original.headers.Authorization = `Bearer ${data.accessToken}`;
            return api(original);
        } catch (err) {
            processQueue(err, null);
            clearAuth();
            window.location.href = "/login";
            return Promise.reject(err);
        } finally {
            isRefreshing = false;
        }
    }
);

export default api;
