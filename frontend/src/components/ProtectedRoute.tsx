import {Navigate, Outlet} from "react-router-dom";
import {useAuthStore} from "@/store/authStore";

const ROLE_LEVEL = {READER: 1, COLLABORATOR: 2, ADMIN: 3} as const;

interface Props {
    requiredRole?: "READER" | "COLLABORATOR" | "ADMIN";
}

export function ProtectedRoute({requiredRole}: Props) {
    const {user, accessToken} = useAuthStore();

    if (!accessToken || !user) return <Navigate to="/login" replace/>;

    if (requiredRole && ROLE_LEVEL[user.role] < ROLE_LEVEL[requiredRole]) {
        return <Navigate to="/" replace/>;
    }

    return <Outlet/>;
}
