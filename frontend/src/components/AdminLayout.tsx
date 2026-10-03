import {useState} from "react";
import {Link, NavLink, Outlet, useNavigate} from "react-router-dom";
import {Button} from "@/components/ui/button";
import {performLogout} from "@/lib/logout";
import {Menu, X, BookOpen, LogOut} from "lucide-react";
import {Wordmark} from "@/components/Logo";
import {Macron} from "@/components/Macron";
import {cn} from "@/lib/utils";

const NAV_ITEMS = [
    {label: "Dashboard", path: "/admin/dashboard"},
    {label: "Pendentes", path: "/admin/users/pending"},
    {label: "Usuários", path: "/admin/users"},
    {label: "Tags", path: "/admin/tags"},
    {label: "Submissões", path: "/admin/submissions"},
];

export function AdminLayout() {
    const navigate = useNavigate();
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    async function handleLogout() {
        await performLogout();
        navigate("/login");
    }

    return (
        <div className="min-h-screen bg-background">
            <header className="flex h-14 md:h-16 items-stretch justify-between gap-6 border-b pl-4 pr-2 md:px-8">
                <div className="flex items-stretch gap-9">
                    {/* items-baseline: o SVG termina na linha de base das letras, então alinha com "Admin" */}
                    <Link to="/admin/dashboard" className="flex items-center">
                        <span className="flex items-baseline gap-2 md:gap-2.5 text-base md:text-xl font-medium">
                            <Wordmark className="h-4 md:h-5 w-auto"/>
                            <span className="text-muted-foreground">Admin</span>
                        </span>
                    </Link>

                    <nav aria-label="Administração" className="hidden md:flex items-stretch gap-1">
                        {NAV_ITEMS.map((item) => (
                            <NavLink
                                key={item.path}
                                to={item.path}
                                end
                                className={({isActive}) => cn(
                                    "relative flex items-center px-3.5 text-sm font-medium transition-colors",
                                    isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                                )}
                            >
                                {({isActive}) => (
                                    <>
                                        {isActive && <Macron className="absolute left-1/2 top-3.5 -ml-[9px] w-[18px]"/>}
                                        {item.label}
                                    </>
                                )}
                            </NavLink>
                        ))}
                    </nav>
                </div>

                <div className="flex items-center gap-1">
                    <Link to="/biblioteca" className="hidden md:flex h-9 items-center gap-2 px-3 text-sm text-muted-foreground hover:text-foreground">
                        <BookOpen className="size-4"/>
                        Biblioteca
                    </Link>
                    <Button variant="outline" onClick={handleLogout} className="ml-2 hidden h-9 md:flex">
                        <LogOut className="size-4"/>
                        Sair
                    </Button>
                    <Button
                        variant="ghost"
                        size="icon"
                        className="size-11 md:hidden"
                        aria-label={mobileMenuOpen ? "Fechar menu" : "Abrir menu"}
                        aria-expanded={mobileMenuOpen}
                        onClick={() => setMobileMenuOpen((v) => !v)}
                    >
                        {mobileMenuOpen ? <X className="size-5"/> : <Menu className="size-5"/>}
                    </Button>
                </div>
            </header>

            {mobileMenuOpen && (
                <nav aria-label="Administração" className="flex flex-col border-b bg-card px-2 py-2 md:hidden">
                    {NAV_ITEMS.map((item) => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            end
                            onClick={() => setMobileMenuOpen(false)}
                            className={({isActive}) => cn(
                                "flex h-11 items-center gap-3 px-3 text-sm font-medium",
                                isActive ? "text-foreground" : "text-muted-foreground"
                            )}
                        >
                            {({isActive}) => (
                                <>
                                    <Macron className={isActive ? "" : "invisible"}/>
                                    {item.label}
                                </>
                            )}
                        </NavLink>
                    ))}
                    <div className="my-1 border-t"/>
                    <Link to="/biblioteca" onClick={() => setMobileMenuOpen(false)} className="flex h-11 items-center gap-3 px-3 text-sm text-muted-foreground">
                        <BookOpen className="size-4"/>
                        Biblioteca
                    </Link>
                    <button type="button" onClick={handleLogout} className="flex h-11 items-center gap-3 px-3 text-left text-sm text-destructive">
                        <LogOut className="size-4"/>
                        Sair
                    </button>
                </nav>
            )}

            <main>
                <Outlet/>
            </main>
        </div>
    );
}
