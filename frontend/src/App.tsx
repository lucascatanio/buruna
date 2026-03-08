import {BrowserRouter, Routes, Route, Navigate} from "react-router-dom";
import {Toaster} from "@/components/ui/sonner";
import {ProtectedRoute} from "@/components/ProtectedRoute";
import {LoginPage} from "@/pages/LoginPage";
import {RegisterPage} from "@/pages/RegisterPage";
import {PendingUsersPage} from "@/pages/admin/PendingUsersPage";
import {UsersPage} from "@/pages/admin/UsersPage";

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="/register" element={<RegisterPage/>}/>

                <Route element={<ProtectedRoute/>}>
                    <Route path="/" element={<Navigate to="/admin/users/pending" replace/>}/>
                </Route>

                <Route element={<ProtectedRoute requiredRole="ADMIN"/>}>
                    <Route path="/admin/users/pending" element={<PendingUsersPage/>}/>
                    <Route path="/admin/users" element={<UsersPage/>}/>
                </Route>

                <Route path="*" element={<Navigate to="/" replace/>}/>
            </Routes>
            <Toaster richColors position="top-right"/>
        </BrowserRouter>
    );
}
