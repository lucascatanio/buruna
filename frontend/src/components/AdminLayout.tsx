import {Outlet, useNavigate} from "react-router-dom";
import {Button} from "@/components/ui/button";
import {useAuthStore} from "@/store/authStore";

export function AdminLayout() {
    const navigate = useNavigate();
    const clearAuth = useAuthStore((s) => s.clearAuth);

    function handleLogout() {
        clearAuth();
        navigate("/login");
    }

    return (
        <div className="min-h-screen bg-background">
            <header className="border-b px-6 py-4 flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <h1 className="text-xl font-semibold">Burūna Admin</h1>
                    <nav className="flex gap-2">
                        <Button variant="ghost" size="sm" onClick={() => navigate("/admin/users/pending")}>
                            Pending
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => navigate("/admin/users")}>
                            All Users
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => navigate("/admin/tags")}>
                            Tags/Categories
                        </Button>
                    </nav>
                </div>
                <Button variant="outline" size="sm" onClick={handleLogout}>
                    Sign out
                </Button>
            </header>

            <main>
                <Outlet/>
            </main>
        </div>
    );
}
