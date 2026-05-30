import {useState} from "react";
import {Outlet, useNavigate, useLocation} from "react-router-dom";
import {Button} from "@/components/ui/button";
import {useAuthStore} from "@/store/authStore";
import {Menu, X, BookOpen} from "lucide-react";

const NAV_ITEMS = [
    {label: "Dashboard", path: "/admin/dashboard"},
    {label: "Pendentes", path: "/admin/users/pending"},
    {label: "Usuários", path: "/admin/users"},
    {label: "Tags", path: "/admin/tags"},
    {label: "Submissões", path: "/admin/submissions"},
];

export function AdminLayout() {
    const navigate = useNavigate();
    const location = useLocation();
    const clearAuth = useAuthStore((s) => s.clearAuth);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    function handleLogout() {
        clearAuth();
        navigate("/login");
    }

    function handleNav(path: string) {
        navigate(path);
        setMobileMenuOpen(false);
    }

    const isActive = (path: string) => location.pathname === path;

    return (
        <div className="min-h-screen bg-background">
            <header className="border-b px-4 md:px-6 py-3 flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <h1 className="text-base md:text-xl font-semibold">Burūna Admin</h1>

                    {/* Desktop nav */}
                    <nav className="hidden md:flex gap-1">
                        {NAV_ITEMS.map((item) => (
                            <Button
                                key={item.path}
                                variant={isActive(item.path) ? "secondary" : "ghost"}
                                size="sm"
                                onClick={() => navigate(item.path)}
                            >
                                {item.label}
                            </Button>
                        ))}
                    </nav>
                </div>

                <div className="flex items-center gap-2">
                    <Button
                        variant="ghost"
                        size="sm"
                        className="hidden md:flex"
                        onClick={() => navigate("/biblioteca")}
                    >
                        <BookOpen className="w-4 h-4 mr-1.5"/>
                        Biblioteca
                    </Button>
                    <Button variant="outline" size="sm" onClick={handleLogout} className="hidden md:flex">
                        Sair
                    </Button>
                    <Button
                        variant="ghost"
                        size="icon"
                        className="md:hidden"
                        onClick={() => setMobileMenuOpen((v) => !v)}
                    >
                        {mobileMenuOpen ? <X className="w-5 h-5"/> : <Menu className="w-5 h-5"/>}
                    </Button>
                </div>
            </header>

            {mobileMenuOpen && (
                <div className="md:hidden border-b bg-background px-4 py-2 flex flex-col gap-1">
                    {NAV_ITEMS.map((item) => (
                        <Button
                            key={item.path}
                            variant={isActive(item.path) ? "secondary" : "ghost"}
                            size="sm"
                            className="w-full justify-start"
                            onClick={() => handleNav(item.path)}
                        >
                            {item.label}
                        </Button>
                    ))}
                    <Button
                        variant="ghost"
                        size="sm"
                        className="w-full justify-start"
                        onClick={() => handleNav("/biblioteca")}
                    >
                        <BookOpen className="w-4 h-4 mr-1.5"/>
                        Biblioteca
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        className="w-full justify-start text-destructive hover:text-destructive"
                        onClick={handleLogout}
                    >
                        Sair
                    </Button>
                </div>
            )}

            <main>
                <Outlet/>
            </main>
        </div>
    );
}
