import axios from "axios";

/** Mensagem do ErrorResponse do backend ({status, error, message, ...}), ou o fallback. */
export function apiErrorMessage(error: unknown, fallback: string): string {
    if (axios.isAxiosError(error)) {
        const message = (error.response?.data as {message?: unknown} | undefined)?.message;
        if (typeof message === "string" && message.trim() !== "") return message;
    }
    return fallback;
}
