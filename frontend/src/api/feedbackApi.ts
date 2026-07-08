import api from "@/lib/axios";

export function sendFeedback(message: string): Promise<void> {
    return api.post("/feedback", {message}).then(() => undefined);
}
