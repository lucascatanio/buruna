export interface UserStorage {
    userId: string;
    username: string;
    usedGb: number;
    quotaGb: number;
}

export interface DashboardData {
    activeUsers: number;
    totalStorageUsedGb: number;
    storageByUser: UserStorage[];
}

export interface PendingUser {
    id: string;
    email: string;
    username: string;
    presentationMessage: string;
    createdAt: string;
}

export interface AdminUser {
    id: string;
    email: string;
    username: string;
    role: string;
    status: string;
    quotaGb: number;
    createdAt: string;
}
