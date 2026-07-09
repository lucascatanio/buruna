import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {AdminUser, DashboardData, PendingUser} from "@/types/admin";
import type {PendingSubmission} from "@/types/manga";

export function getDashboard(): Promise<DashboardData> {
    return api.get<DashboardData>("/admin/dashboard").then((r) => r.data);
}

export function listPendingSubmissions(): Promise<Page<PendingSubmission>> {
    return api.get<Page<PendingSubmission>>("/admin/submissions?size=50&sort=submittedAt,asc").then((r) => r.data);
}

export function approveSubmission(id: string): Promise<void> {
    return api.post(`/admin/submissions/${id}/approve`).then(() => undefined);
}

export function rejectSubmission(id: string, rejectionReason: string | null): Promise<void> {
    return api.post(`/admin/submissions/${id}/reject`, {rejectionReason}).then(() => undefined);
}

export function listPendingUsers(): Promise<Page<PendingUser>> {
    return api.get<Page<PendingUser>>("/admin/users/pending?size=50&sort=createdAt,asc").then((r) => r.data);
}

export function approveUser(id: string): Promise<void> {
    return api.post(`/admin/users/${id}/approve`, {}).then(() => undefined);
}

export function rejectUser(id: string, reason: string | null): Promise<void> {
    return api.post(`/admin/users/${id}/reject`, {reason}).then(() => undefined);
}

export function listUsers(page: number, size = 20): Promise<Page<AdminUser>> {
    return api.get<Page<AdminUser>>(`/admin/users?page=${page}&size=${size}&sort=createdAt,desc`).then((r) => r.data);
}

export function updateUserRole(id: string, role: string): Promise<void> {
    return api.patch(`/admin/users/${id}/role`, {role}).then(() => undefined);
}

export function updateUserStatus(id: string, status: string): Promise<void> {
    return api.patch(`/admin/users/${id}/status`, {status}).then(() => undefined);
}

export function updateUserQuota(id: string, quotaGb: number): Promise<void> {
    return api.patch(`/admin/users/${id}/quota`, {quotaGb}).then(() => undefined);
}
