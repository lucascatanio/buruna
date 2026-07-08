import {useEffect, useState} from "react";
import {listTagCategories, listTags} from "@/api/mangaApi";
import type {Tag, TagCategory} from "@/types/manga";
import {Badge} from "@/components/ui/badge";
import {X} from "lucide-react";

interface TagSelectorProps {
    selectedIds: string[];
    onChange: (ids: string[]) => void;
    excludeCategories?: string[];
}

export function TagSelector({selectedIds, onChange, excludeCategories = []}: TagSelectorProps) {
    const [categories, setCategories] = useState<TagCategory[]>([]);
    const [tags, setTags] = useState<Tag[]>([]);

    useEffect(() => {
        Promise.all([
            listTagCategories(),
            listTags(),
        ]).then(([cats, tagList]) => {
            setCategories(cats);
            setTags(tagList);
        });
    }, []);

    const excluded = excludeCategories.map((c) => c.toLowerCase());

    const visibleCategories = categories.filter(
        (cat) => !excluded.includes(cat.name.toLowerCase())
    );

    const toggle = (id: string) => {
        onChange(
            selectedIds.includes(id)
                ? selectedIds.filter((s) => s !== id)
                : [...selectedIds, id]
        );
    };

    const selectedTags = tags.filter((t) => selectedIds.includes(t.id));

    return (
        <div className="space-y-3">
            {selectedTags.length > 0 && (
                <div className="flex flex-wrap gap-1">
                    {selectedTags.map((t) => (
                        <Badge
                            key={t.id}
                            variant="default"
                            className="cursor-pointer gap-1"
                            onClick={() => toggle(t.id)}
                        >
                            {t.name}
                            <X className="w-3 h-3"/>
                        </Badge>
                    ))}
                </div>
            )}

            <div className="border rounded-md p-3 space-y-3 max-h-64 overflow-y-auto">
                {visibleCategories.map((cat) => {
                    const catTags = tags.filter((t) => t.category.id === cat.id);
                    if (catTags.length === 0) return null;
                    return (
                        <div key={cat.id}>
                            <p className="text-xs font-semibold text-muted-foreground uppercase mb-1">
                                {cat.name}
                            </p>
                            <div className="flex flex-wrap gap-1">
                                {catTags.map((tag) => (
                                    <Badge
                                        key={tag.id}
                                        variant={selectedIds.includes(tag.id) ? "default" : "outline"}
                                        className="cursor-pointer"
                                        onClick={() => toggle(tag.id)}
                                    >
                                        {tag.name}
                                    </Badge>
                                ))}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
