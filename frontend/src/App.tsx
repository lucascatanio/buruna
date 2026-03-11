import {BrowserRouter, Routes, Route, Navigate} from "react-router-dom";
import {Toaster} from "@/components/ui/sonner";
import {ProtectedRoute} from "@/components/ProtectedRoute";
import {AppLayout} from "@/components/AppLayout";
import {AdminLayout} from "@/components/AdminLayout";
import {LoginPage} from "@/pages/LoginPage";
import {RegisterPage} from "@/pages/RegisterPage";
import {LibraryPage} from "@/pages/LibraryPage";
import {MangaDetailPage} from "@/pages/MangaDetailPage";
import {MangaUploadPage} from "@/pages/MangaUploadPage";
import {MangaEditPage} from "@/pages/MangaEditPage";
import {PendingUsersPage} from "@/pages/admin/PendingUsersPage";
import {UsersPage} from "@/pages/admin/UsersPage";
import {TagsPage} from "@/pages/admin/TagsPage";

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="/register" element={<RegisterPage/>}/>

                <Route element={<ProtectedRoute/>}>
                    <Route element={<AppLayout/>}>
                        <Route path="/" element={<Navigate to="/biblioteca" replace/>}/>
                        <Route path="/biblioteca" element={<LibraryPage/>}/>
                        <Route path="/biblioteca/:slug" element={<MangaDetailPage/>}/>
                    </Route>
                </Route>

                <Route element={<ProtectedRoute requiredRole="COLLABORATOR"/>}>
                    <Route element={<AppLayout/>}>
                        <Route path="/mangas/novo" element={<MangaUploadPage/>}/>
                        <Route path="/mangas/:id/editar" element={<MangaEditPage/>}/>
                    </Route>
                </Route>

                <Route element={<ProtectedRoute requiredRole="ADMIN"/>}>
                    <Route element={<AdminLayout/>}>
                        <Route path="/admin/users/pending" element={<PendingUsersPage/>}/>
                        <Route path="/admin/users" element={<UsersPage/>}/>
                        <Route path="/admin/tags" element={<TagsPage/>}/>
                    </Route>
                </Route>

                <Route path="*" element={<Navigate to="/" replace/>}/>
            </Routes>
            <Toaster richColors position="top-right"/>
        </BrowserRouter>
    );
}