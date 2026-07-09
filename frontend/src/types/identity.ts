export interface LoginResponse {
    requires2FA: boolean;
    tempToken?: string;
    accessToken?: string;
    refreshToken?: string;
    expiresIn?: number;
}

export interface RegisterRequest {
    email: string;
    username: string;
    password: string;
    presentationMessage: string;
    captchaToken: string;
}

export interface ResetInfoResponse {
    totpRequired: boolean;
}

export interface ResetPasswordRequest {
    token: string;
    newPassword: string;
    totpCode?: string;
}

export interface TotpStatusResponse {
    totpEnabled: boolean;
}

export interface TotpSetupResponse {
    secret: string;
    qrUri: string;
}
