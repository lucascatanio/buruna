import {Outlet, useNavigate, useLocation} from "react-router-dom";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {BookOpen, Library, Settings, LogOut, History, BookMarked} from "lucide-react";

export function AppLayout() {
    const navigate = useNavigate();
    const location = useLocation();
    const user = useAuthStore((s) => s.user);
    const clearAuth = useAuthStore((s) => s.clearAuth);

    function handleLogout() {
        clearAuth();
        navigate("/login");
    }

    const isActive = (path: string) => location.pathname.startsWith(path);
    const isAdmin = user?.role === "ADMIN";

    return (
        <div className="min-h-screen bg-background flex flex-col">
            <header className="border-b px-6 py-3 hidden md:flex items-center justify-between">
                <div className="flex items-center gap-6">
                    <span
                        className="text-lg font-semibold cursor-pointer select-none"
                        onClick={() => navigate("/biblioteca")}
                    >
                        Burūna
                    </span>
                    <nav className="flex gap-1">
                        <Button
                            variant={isActive("/biblioteca") ? "secondary" : "ghost"}
                            size="sm"
                            onClick={() => navigate("/biblioteca")}
                        >
                            <BookOpen className="w-4 h-4 mr-1.5"/>
                            Biblioteca
                        </Button>
                        <Button
                            variant={isActive("/colecao") ? "secondary" : "ghost"}
                            size="sm"
                            onClick={() => navigate("/colecao")}
                        >
                            <Library className="w-4 h-4 mr-1.5"/>
                            Minha Coleção
                        </Button>
                        <Button
                            variant={isActive("/lista") ? "secondary" : "ghost"}
                            size="sm"
                            onClick={() => navigate("/lista")}
                        >
                            <BookMarked className="w-4 h-4 mr-1.5"/>
                            Lista
                        </Button>
                        <Button
                            variant={isActive("/historico") ? "secondary" : "ghost"}
                            size="sm"
                            onClick={() => navigate("/historico")}
                        >
                            <History className="w-4 h-4 mr-1.5"/>
                            Histórico
                        </Button>
                        {isAdmin && (
                            <>
                                <Button
                                    variant={isActive("/admin/users") ? "secondary" : "ghost"}
                                    size="sm"
                                    onClick={() => navigate("/admin/dashboard")}
                                >
                                    <Settings className="w-4 h-4 mr-1.5"/>
                                    Admin
                                </Button>
                            </>
                        )}
                    </nav>
                </div>
                <Button variant="outline" size="sm" onClick={handleLogout}>
                    <LogOut className="w-4 h-4 mr-1.5"/>
                    Sair
                </Button>
            </header>

            <header className="border-b px-4 py-3 flex md:hidden items-center justify-between">
                <span className="text-lg font-semibold select-none">Burūna</span>
                <Button variant="ghost" size="icon" onClick={handleLogout}>
                    <LogOut className="w-4 h-4"/>
                </Button>
            </header>

            <main className="flex-1 pb-20 md:pb-0">
                <Outlet/>
            </main>

            <nav className="fixed bottom-0 left-0 right-0 border-t bg-background flex md:hidden z-40">
                <MobileNavItem
                    icon={<BookOpen className="w-5 h-5"/>}
                    label="Biblioteca"
                    active={isActive("/biblioteca")}
                    onClick={() => navigate("/biblioteca")}
                />
                <MobileNavItem
                    icon={<Library className="w-5 h-5"/>}
                    label="Coleção"
                    active={isActive("/colecao")}
                    onClick={() => navigate("/colecao")}
                />
                <MobileNavItem
                    icon={<BookMarked className="w-5 h-5"/>}
                    label="Lista"
                    active={isActive("/lista")}
                    onClick={() => navigate("/lista")}
                />
                <MobileNavItem
                    icon={<History className="w-5 h-5"/>}
                    label="Histórico"
                    active={isActive("/historico")}
                    onClick={() => navigate("/historico")}
                />
                {isAdmin && (
                    <MobileNavItem
                        icon={<Settings className="w-5 h-5"/>}
                        label="Admin"
                        active={isActive("/admin")}
                        onClick={() => navigate("/admin/dashboard")}
                    />
                )}
            </nav>
        </div>
    );
}

interface MobileNavItemProps {
    icon: React.ReactNode;
    label: string;
    active: boolean;
    onClick?: () => void;
    disabled?: boolean;
}

function MobileNavItem({icon, label, active, onClick, disabled}: MobileNavItemProps) {
    return (
        <button
            className={`flex-1 flex flex-col items-center justify-center py-2.5 gap-0.5 text-[11px] font-medium transition-colors
                ${active ? "text-primary" : "text-muted-foreground"}
                ${disabled ? "opacity-35 cursor-not-allowed" : "hover:text-foreground"}
            `}
            onClick={disabled ? undefined : onClick}
            disabled={disabled}
        >
            {icon}
            {label}
        </button>
    );
}