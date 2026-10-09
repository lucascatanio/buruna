import {useEffect} from "react";
import {BrowserRouter, Routes, Route, Navigate} from "react-router-dom";
import {Toaster} from "@/components/ui/sonner";
import {bootstrapAuth} from "@/lib/authBootstrap";
import {ProtectedRoute} from "@/components/ProtectedRoute";
import {AppLayout} from "@/components/AppLayout";
import {AdminLayout} from "@/components/AdminLayout";
import {LoginPage} from "@/pages/LoginPage";
import {RegisterPage} from "@/pages/RegisterPage";
import {LibraryPage} from "@/pages/LibraryPage";
import {WorkDetailPage} from "@/pages/WorkDetailPage";
import {WorkUploadPage} from "@/pages/WorkUploadPage";
import {WorkEditPage} from "@/pages/WorkEditPage";
import {PendingUsersPage} from "@/pages/admin/PendingUsersPage";
import {PendingSubmissionsPage} from "@/pages/admin/PendingSubmissionsPage";
import {UsersPage} from "@/pages/admin/UsersPage";
import {TagsPage} from "@/pages/admin/TagsPage";
import {MyCollectionPage} from "@/pages/MyCollectionPage.tsx";
import {PrivateWorkUploadPage} from "@/pages/PrivateWorkUploadPage.tsx";
import {PrivateWorkDetailPage} from "@/pages/PrivateWorkDetailPage.tsx";
import {ReadingHistoryPage} from "@/pages/ReadingHistoryPage.tsx";
import {ReadingListPage} from "@/pages/ReadingListPage.tsx";
import {ReaderPage} from "@/pages/ReaderPage.tsx";
import {useParams} from "react-router-dom";

function ReaderPageWrapper() {
    const {volumeId} = useParams();
    return <ReaderPage key={volumeId}/>;
}

// rota antiga, de antes do rename mangá → obra (ADR-45): links salvos continuam funcionando
function LegacyWorkEditRedirect() {
    const {id} = useParams();
    return <Navigate to={`/obras/${id}/editar`} replace/>;
}
import {AdminDashboardPage} from "@/pages/AdminDashboardPage.tsx";
import {ForgotPasswordPage} from "@/pages/ForgotPasswordPage.tsx";
import {ResetPasswordPage} from "@/pages/ResetPasswordPage.tsx";
import {SecuritySettingsPage} from "@/pages/SecuritySettingsPage.tsx";

export default function App() {
    useEffect(() => {
        bootstrapAuth();
    }, []);

    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="/register" element={<RegisterPage/>}/>
                <Route path="/forgot-password" element={<ForgotPasswordPage/>}/>
                <Route path="/reset-password" element={<ResetPasswordPage/>}/>

                <Route element={<ProtectedRoute/>}>
                    <Route element={<AppLayout/>}>
                        <Route path="/" element={<Navigate to="/biblioteca" replace/>}/>
                        <Route path="/biblioteca" element={<LibraryPage/>}/>
                        <Route path="/biblioteca/:slug" element={<WorkDetailPage/>}/>
                        <Route path="/colecao" element={<MyCollectionPage/>}/>
                        <Route path="/colecao/novo" element={<PrivateWorkUploadPage/>}/>
                        <Route path="/colecao/:id" element={<PrivateWorkDetailPage/>}/>
                        <Route path="/historico" element={<ReadingHistoryPage/>}/>
                        <Route path="/lista" element={<ReadingListPage/>}/>
                        <Route path="/seguranca" element={<SecuritySettingsPage/>}/>
                    </Route>
                    <Route element={<ProtectedRoute/>}>
                        <Route path="/leitor/:volumeId" element={<ReaderPageWrapper/>}/>
                    </Route>
                </Route>

                <Route element={<ProtectedRoute requiredRole="COLLABORATOR"/>}>
                    <Route element={<AppLayout/>}>
                        <Route path="/obras/nova" element={<WorkUploadPage/>}/>
                        <Route path="/obras/:id/editar" element={<WorkEditPage/>}/>
                        <Route path="/mangas/novo" element={<Navigate to="/obras/nova" replace/>}/>
                        <Route path="/mangas/:id/editar" element={<LegacyWorkEditRedirect/>}/>
                    </Route>
                </Route>

                <Route element={<ProtectedRoute requiredRole="ADMIN"/>}>
                    <Route element={<AdminLayout/>}>
                        <Route path="/admin/users/pending" element={<PendingUsersPage/>}/>
                        <Route path="/admin/users" element={<UsersPage/>}/>
                        <Route path="/admin/tags" element={<TagsPage/>}/>
                        <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
                        <Route path="/admin/submissions" element={<PendingSubmissionsPage/>}/>
                    </Route>
                </Route>

                <Route path="*" element={<Navigate to="/" replace/>}/>
            </Routes>
            <Toaster richColors position="top-right"/>
        </BrowserRouter>
    );
}