import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import api from "@/lib/axios";
import { useAuthStore } from "@/store/authStore";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface User {
    id: string;
    email: string;
    username: string;
    role: string;
    status: string;
    quotaGb: number;
    createdAt: string;
}

interface Page<T> {
    content: T[];
    totalPages: number;
    number: number;
}

const ROLE_OPTIONS = ["READER", "COLLABORATOR", "ADMIN"];
const STATUS_OPTIONS = ["ACTIVE", "INACTIVE", "PENDING"];

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
    ACTIVE: "default",
    PENDING: "secondary",
    INACTIVE: "destructive",
};

export function UsersPage() {
    const navigate = useNavigate();
    const clearAuth = useAuthStore((s) => s.clearAuth);

    const [page, setPage] = useState<Page<User>>({ content: [], totalPages: 0, number: 0 });
    const [loading, setLoading] = useState(true);
    const [editingUser, setEditingUser] = useState<User | null>(null);
    const [editForm, setEditForm] = useState({ role: "", status: "", quotaGb: "" });

    async function fetchUsers(pageNumber = 0) {
        setLoading(true);
        try {
            const { data } = await api.get(`/admin/users?page=${pageNumber}&size=20&sort=createdAt,desc`);
            setPage(data);
        } catch {
            toast.error("Failed to load users");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => { fetchUsers(); }, []);

    function openEdit(user: User) {
        setEditingUser(user);
        setEditForm({
            role: user.role,
            status: user.status,
            quotaGb: String(user.quotaGb),
        });
    }

    async function handleSave() {
        if (!editingUser) return;

        try {
            const requests = [];

            if (editForm.role !== editingUser.role) {
                requests.push(api.patch(`/admin/users/${editingUser.id}/role`, { role: editForm.role }));
            }
            if (editForm.status !== editingUser.status) {
                requests.push(api.patch(`/admin/users/${editingUser.id}/status`, { status: editForm.status }));
            }
            if (Number(editForm.quotaGb) !== editingUser.quotaGb) {
                requests.push(api.patch(`/admin/users/${editingUser.id}/quota`, { quotaGb: Number(editForm.quotaGb) }));
            }

            await Promise.all(requests);
            toast.success("User updated");
            setEditingUser(null);
            fetchUsers(page.number);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Failed to update user");
        }
    }

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
                    </nav>
                </div>
                <Button variant="outline" size="sm" onClick={handleLogout}>
                    Sign out
                </Button>
            </header>

            <main className="max-w-5xl mx-auto px-6 py-8">
                <h2 className="text-2xl font-bold mb-6">All users</h2>

                {loading && <p className="text-muted-foreground">Loading…</p>}

                {!loading && (
                    <>
                        <div className="space-y-2">
                            {page.content.map((user) => (
                                <Card key={user.id}>
                                    <CardContent className="py-4 flex items-center justify-between">
                                        <div className="flex items-center gap-4">
                                            <div>
                                                <p className="font-medium text-sm">{user.username}</p>
                                                <p className="text-xs text-muted-foreground">{user.email}</p>
                                            </div>
                                            <Badge variant={STATUS_VARIANT[user.status] ?? "outline"}>
                                                {user.status}
                                            </Badge>
                                            <Badge variant="outline">{user.role}</Badge>
                                            <span className="text-xs text-muted-foreground">{user.quotaGb} GB</span>
                                        </div>
                                        <Button size="sm" variant="outline" onClick={() => openEdit(user)}>
                                            Edit
                                        </Button>
                                    </CardContent>
                                </Card>
                            ))}
                        </div>

                        {page.totalPages > 1 && (
                            <div className="flex justify-center gap-2 mt-6">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={page.number === 0}
                                    onClick={() => fetchUsers(page.number - 1)}
                                >
                                    Previous
                                </Button>
                                <span className="text-sm self-center text-muted-foreground">
                  {page.number + 1} / {page.totalPages}
                </span>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={page.number + 1 >= page.totalPages}
                                    onClick={() => fetchUsers(page.number + 1)}
                                >
                                    Next
                                </Button>
                            </div>
                        )}
                    </>
                )}
            </main>

            {/* Edit modal */}
            {editingUser && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
                    <Card className="w-full max-w-sm">
                        <CardContent className="pt-6 space-y-4">
                            <h3 className="font-semibold text-lg">Edit {editingUser.username}</h3>

                            <div className="space-y-2">
                                <Label>Role</Label>
                                <select
                                    className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                    value={editForm.role}
                                    onChange={(e) => setEditForm((p) => ({ ...p, role: e.target.value }))}
                                >
                                    {ROLE_OPTIONS.map((r) => <option key={r}>{r}</option>)}
                                </select>
                            </div>

                            <div className="space-y-2">
                                <Label>Status</Label>
                                <select
                                    className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                    value={editForm.status}
                                    onChange={(e) => setEditForm((p) => ({ ...p, status: e.target.value }))}
                                >
                                    {STATUS_OPTIONS.map((s) => <option key={s}>{s}</option>)}
                                </select>
                            </div>

                            <div className="space-y-2">
                                <Label>Quota (GB)</Label>
                                <Input
                                    type="number"
                                    min="0.1"
                                    step="0.5"
                                    value={editForm.quotaGb}
                                    onChange={(e) => setEditForm((p) => ({ ...p, quotaGb: e.target.value }))}
                                />
                            </div>

                            <div className="flex gap-2 pt-2">
                                <Button className="flex-1" onClick={handleSave}>Save</Button>
                                <Button className="flex-1" variant="outline" onClick={() => setEditingUser(null)}>
                                    Cancel
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                </div>
            )}
        </div>
    );
}
