import {useEffect, useState} from "react";
import {createTag, createTagCategory, deleteTag, listTagCategories, listTags, updateTag} from "@/api/mangaApi";
import type {Tag, TagCategory} from "@/types/manga";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {toast} from "sonner";
import {Pencil, Trash2, Plus, X, Check} from "lucide-react";

export function TagsPage() {
    const [categories, setCategories] = useState<TagCategory[]>([]);
    const [tags, setTags] = useState<Tag[]>([]);
    const [newCategoryName, setNewCategoryName] = useState("");
    const [newTag, setNewTag] = useState({name: "", slug: "", categoryId: ""});
    const [editingTag, setEditingTag] = useState<Tag | null>(null);
    const [editForm, setEditForm] = useState({name: "", slug: "", categoryId: ""});
    const [loading, setLoading] = useState(false);

    const fetchData = async () => {
        const [cats, tagList] = await Promise.all([
            listTagCategories(),
            listTags(),
        ]);
        setCategories(cats);
        setTags(tagList);
    };

    useEffect(() => {
        fetchData();
    }, []);

    const slugify = (value: string) =>
        value.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
            .replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");

    const handleCreateCategory = async () => {
        if (!newCategoryName.trim()) return;
        setLoading(true);
        try {
            await createTagCategory(newCategoryName.trim());
            toast.success("Categoria criada!");
            setNewCategoryName("");
            await fetchData();
        } catch (e: any) {
            toast.error(e.response?.data?.message ?? "Erro ao criar categoria");
        } finally {
            setLoading(false);
        }
    };

    const handleCreateTag = async () => {
        if (!newTag.name.trim() || !newTag.slug.trim() || !newTag.categoryId) return;
        setLoading(true);
        try {
            await createTag(newTag);
            toast.success("Tag criada!");
            setNewTag({name: "", slug: "", categoryId: ""});
            await fetchData();
        } catch (e: any) {
            toast.error(e.response?.data?.message ?? "Erro ao criar tag");
        } finally {
            setLoading(false);
        }
    };

    const handleEditSave = async () => {
        if (!editingTag) return;
        setLoading(true);
        try {
            await updateTag(editingTag.id, editForm);
            toast.success("Tag atualizada!");
            setEditingTag(null);
            await fetchData();
        } catch (e: any) {
            toast.error(e.response?.data?.message ?? "Erro ao atualizar tag");
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteTag = async (tag: Tag) => {
        if (!confirm(`Deletar a tag "${tag.name}"?`)) return;
        try {
            await deleteTag(tag.id);
            toast.success("Tag removida!");
            await fetchData();
        } catch (e: any) {
            toast.error(e.response?.data?.message ?? "Erro ao remover tag");
        }
    };

    const startEdit = (tag: Tag) => {
        setEditingTag(tag);
        setEditForm({name: tag.name, slug: tag.slug, categoryId: tag.category.id});
    };

    const tagsByCategory = categories.map((cat) => ({
        category: cat,
        tags: tags.filter((t) => t.category.id === cat.id),
    }));

    return (
        <div className="max-w-4xl mx-auto p-6 space-y-8">
            <h1 className="text-2xl font-bold">Gerenciar Tags</h1>

            <Card>
                <CardHeader><CardTitle className="text-base">Nova Categoria</CardTitle></CardHeader>
                <CardContent className="flex gap-2">
                    <Input
                        placeholder="Nome da categoria"
                        value={newCategoryName}
                        onChange={(e) => setNewCategoryName(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleCreateCategory()}
                    />
                    <Button onClick={handleCreateCategory} disabled={loading}>
                        <Plus className="w-4 h-4 mr-1"/> Criar
                    </Button>
                </CardContent>
            </Card>

            <Card>
                <CardHeader><CardTitle className="text-base">Nova Tag</CardTitle></CardHeader>
                <CardContent className="grid grid-cols-1 sm:grid-cols-4 gap-2">
                    <div className="sm:col-span-1">
                        <Label>Categoria</Label>
                        <select
                            className="w-full mt-1 rounded-md border border-input bg-background px-3 py-2 text-sm"
                            value={newTag.categoryId}
                            onChange={(e) => setNewTag((p) => ({...p, categoryId: e.target.value}))}
                        >
                            <option value="">Selecione...</option>
                            {categories.map((c) => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                        </select>
                    </div>
                    <div className="sm:col-span-1">
                        <Label>Nome</Label>
                        <Input
                            placeholder="Ex: Ação"
                            value={newTag.name}
                            onChange={(e) => setNewTag((p) => ({
                                ...p,
                                name: e.target.value,
                                slug: slugify(e.target.value)
                            }))}
                        />
                    </div>
                    <div className="sm:col-span-1">
                        <Label>Slug</Label>
                        <Input
                            placeholder="ex: acao"
                            value={newTag.slug}
                            onChange={(e) => setNewTag((p) => ({...p, slug: e.target.value}))}
                        />
                    </div>
                    <div className="sm:col-span-1 flex items-end">
                        <Button className="w-full" onClick={handleCreateTag} disabled={loading}>
                            <Plus className="w-4 h-4 mr-1"/> Criar
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {tagsByCategory.map(({category, tags: catTags}) => (
                <Card key={category.id}>
                    <CardHeader>
                        <CardTitle className="text-sm text-muted-foreground uppercase tracking-wide">
                            {category.name} ({catTags.length})
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="flex flex-wrap gap-2">
                        {catTags.length === 0 && (
                            <span className="text-sm text-muted-foreground">Nenhuma tag</span>
                        )}
                        {catTags.map((tag) =>
                            editingTag?.id === tag.id ? (
                                <div key={tag.id}
                                     className="flex items-center gap-2 border rounded-md px-3 py-1.5 bg-muted">
                                    <Input
                                        className="h-7 w-32 text-sm"
                                        value={editForm.name}
                                        onChange={(e) => setEditForm((p) => ({
                                            ...p,
                                            name: e.target.value,
                                            slug: slugify(e.target.value)
                                        }))}
                                        onKeyDown={(e) => e.key === "Enter" && handleEditSave()}
                                        autoFocus
                                    />
                                    <Button size="sm" variant="default" onClick={handleEditSave} disabled={loading}>
                                        <Check className="w-3 h-3 mr-1"/> Salvar
                                    </Button>
                                    <Button size="sm" variant="ghost" onClick={() => setEditingTag(null)}>
                                        <X className="w-3 h-3 mr-1"/> Cancelar
                                    </Button>
                                </div>
                            ) : (
                                <div key={tag.id}
                                     className="flex items-center justify-between gap-2 border rounded-md px-3 py-1.5 hover:bg-muted/50 transition-colors">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-medium">{tag.name}</span>
                                        <span className="text-xs text-muted-foreground">{tag.slug}</span>
                                    </div>
                                    <div className="flex items-center gap-1 shrink-0">
                                        <Button size="icon" variant="ghost" className="h-7 w-7"
                                                onClick={() => startEdit(tag)}>
                                            <Pencil className="w-3 h-3"/>
                                        </Button>
                                        <Button size="icon" variant="ghost"
                                                className="h-7 w-7 text-destructive hover:text-destructive"
                                                onClick={() => handleDeleteTag(tag)}>
                                            <Trash2 className="w-3 h-3"/>
                                        </Button>
                                    </div>
                                </div>
                            )
                        )}
                    </CardContent>
                </Card>
            ))}
        </div>
    );
}
