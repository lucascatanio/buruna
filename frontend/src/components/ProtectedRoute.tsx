import {Navigate, Outlet} from "react-router-dom";
import {useAuthStore} from "@/store/authStore";
import {LogoMark} from "@/components/Logo";

const ROLE_LEVEL = {READER: 1, COLLABORATOR: 2, ADMIN: 3} as const;

interface Props {
    requiredRole?: "READER" | "COLLABORATOR" | "ADMIN";
}

export function ProtectedRoute({requiredRole}: Props) {
    const {user, accessToken, initialized} = useAuthStore();

    // Aguarda o bootstrap (troca do cookie httpOnly por accessToken) antes de decidir
    // redirecionar — sem isso, um F5 manda todo mundo para /login antes da resposta
    // do /auth/refresh chegar.
    if (!initialized) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-background">
                <LogoMark className="h-12 w-auto animate-pulse" label="Carregando Burūna"/>
            </div>
        );
    }

    if (!accessToken || !user) return <Navigate to="/login" replace/>;

    if (requiredRole && ROLE_LEVEL[user.role] < ROLE_LEVEL[requiredRole]) {
        return <Navigate to="/" replace/>;
    }

    return <Outlet/>;
}
