import api from "@/lib/axios";
import type {
    LoginResponse,
    RegisterRequest,
    ResetInfoResponse,
    ResetPasswordRequest,
    TotpSetupResponse,
    TotpStatusResponse,
} from "@/types/identity";

export function login(email: string, password: string): Promise<LoginResponse> {
    return api.post<LoginResponse>("/auth/login", {email, password}).then((r) => r.data);
}

export function authenticate2FA(tempToken: string, totpCode: string): Promise<LoginResponse> {
    return api.post<LoginResponse>("/auth/2fa/authenticate", {tempToken, totpCode}).then((r) => r.data);
}

export function register(request: RegisterRequest): Promise<void> {
    return api.post("/auth/register", request).then(() => undefined);
}

export function forgotPassword(email: string): Promise<void> {
    return api.post("/auth/password/forgot", {email}).then(() => undefined);
}

export function getResetInfo(token: string): Promise<ResetInfoResponse> {
    return api.get<ResetInfoResponse>("/auth/password/reset-info", {params: {token}}).then((r) => r.data);
}

export function resetPassword(request: ResetPasswordRequest): Promise<void> {
    return api.post("/auth/password/reset", request).then(() => undefined);
}

export function get2FAStatus(): Promise<TotpStatusResponse> {
    return api.get<TotpStatusResponse>("/auth/2fa/status").then((r) => r.data);
}

export function setup2FA(): Promise<TotpSetupResponse> {
    return api.post<TotpSetupResponse>("/auth/2fa/setup").then((r) => r.data);
}

export function verify2FA(code: string): Promise<void> {
    return api.post("/auth/2fa/verify", {code}).then(() => undefined);
}

export function disable2FA(code: string): Promise<void> {
    return api.post("/auth/2fa/disable", {code}).then(() => undefined);
}
