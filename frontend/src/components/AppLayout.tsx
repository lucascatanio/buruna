import type {ComponentType} from "react";
import {Link, NavLink, Outlet, useNavigate} from "react-router-dom";
import {useAuthStore} from "@/store/authStore";
import {performLogout} from "@/lib/logout";
import {Button} from "@/components/ui/button";
import {FeedbackButton} from "@/components/FeedbackDialog";
import {Wordmark} from "@/components/Logo";
import {Macron} from "@/components/Macron";
import {BookOpen, Library, LayoutDashboard, LogOut, History, BookMarked, Shield} from "lucide-react";
import {cn} from "@/lib/utils";

interface NavItem {
    to: string;
    label: string;
    shortLabel?: string;
    icon: ComponentType<{className?: string}>;
}

const NAV: NavItem[] = [
    {to: "/biblioteca", label: "Biblioteca", icon: BookOpen},
    {to: "/colecao", label: "Minha Coleção", shortLabel: "Coleção", icon: Library},
    {to: "/lista", label: "Lista", icon: BookMarked},
    {to: "/historico", label: "Histórico", icon: History},
];

const ADMIN_NAV: NavItem = {to: "/admin/dashboard", label: "Admin", icon: LayoutDashboard};

export function AppLayout() {
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);
    const mobileNav = user?.role === "ADMIN" ? [...NAV, ADMIN_NAV] : NAV;

    async function handleLogout() {
        await performLogout();
        navigate("/login");
    }

    return (
        <div className="min-h-screen bg-background flex flex-col">
            <header className="hidden md:flex h-16 border-b px-8 items-stretch justify-between gap-6">
                <div className="flex items-stretch gap-9">
                    <Link to="/biblioteca" aria-label="Burūna, ir para a biblioteca" className="flex items-center">
                        <Wordmark className="h-[21px] w-auto"/>
                    </Link>
                    <nav aria-label="Principal" className="flex items-stretch gap-1">
                        {NAV.map(({to, label, icon: Icon}) => (
                            <NavLink
                                key={to}
                                to={to}
                                className={({isActive}) => cn(
                                    "relative flex items-center gap-2 px-3.5 text-sm font-medium transition-colors",
                                    isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                                )}
                            >
                                {({isActive}) => (
                                    <>
                                        {isActive && <Macron className="absolute left-1/2 top-3.5 -ml-[9px] w-[18px]"/>}
                                        <Icon className="size-4"/>
                                        {label}
                                    </>
                                )}
                            </NavLink>
                        ))}
                    </nav>
                </div>
                <div className="flex items-center gap-1">
                    {user?.role === "ADMIN" && (
                        <NavLink
                            to={ADMIN_NAV.to}
                            className="flex h-9 items-center gap-2 px-3 text-sm text-muted-foreground hover:text-foreground"
                        >
                            <LayoutDashboard className="size-4"/>
                            Admin
                        </NavLink>
                    )}
                    <NavLink
                        to="/seguranca"
                        className={({isActive}) => cn(
                            "flex h-9 items-center gap-2 px-3 text-sm hover:text-foreground",
                            isActive ? "text-foreground" : "text-muted-foreground"
                        )}
                    >
                        <Shield className="size-4"/>
                        Segurança
                    </NavLink>
                    <Button variant="outline" onClick={handleLogout} className="ml-2 h-9">
                        <LogOut className="size-4"/>
                        Sair
                    </Button>
                </div>
            </header>

            <header className="flex md:hidden h-14 border-b pl-4 pr-2 items-center justify-between">
                <Link to="/biblioteca" aria-label="Burūna, ir para a biblioteca">
                    <Wordmark className="h-[18px] w-auto"/>
                </Link>
                <div className="flex items-center">
                    <Button variant="ghost" size="icon" aria-label="Segurança" onClick={() => navigate("/seguranca")} className="size-11">
                        <Shield className="size-5"/>
                    </Button>
                    <Button variant="ghost" size="icon" aria-label="Sair" onClick={handleLogout} className="size-11">
                        <LogOut className="size-5"/>
                    </Button>
                </div>
            </header>

            <main className="flex-1 pb-20 md:pb-0">
                <Outlet/>
            </main>

            <FeedbackButton/>

            <nav aria-label="Principal" className="fixed bottom-0 left-0 right-0 z-40 flex h-16 border-t bg-card md:hidden">
                {mobileNav.map(({to, label, shortLabel, icon: Icon}) => (
                    <NavLink
                        key={to}
                        to={to}
                        className={({isActive}) => cn(
                            "relative flex flex-1 flex-col items-center justify-center gap-1 text-[11px] font-medium transition-colors",
                            isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                        )}
                    >
                        {({isActive}) => (
                            <>
                                {isActive && <Macron className="absolute left-1/2 top-0 -ml-[11px] w-[22px]"/>}
                                <Icon className="size-5"/>
                                {shortLabel ?? label}
                            </>
                        )}
                    </NavLink>
                ))}
            </nav>
        </div>
    );
}
