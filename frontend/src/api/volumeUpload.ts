/**
 * PUT do arquivo direto na URL assinada (upload em 2 fases, ADR-24/ADR-40). Único ponto que
 * faz esse PUT, para não duplicar o envio dos headers exigidos pela assinatura: o GCS assina
 * `x-goog-content-length-range` (limite de tamanho) e o Content-Type, que o backend devolve em
 * `requiredHeaders`.
 */
export async function uploadSignedFile(
    uploadUrl: string,
    requiredHeaders: Record<string, string>,
    file: File,
): Promise<void> {
    const uploadRes = await fetch(uploadUrl, {
        method: "PUT",
        // fallback para backends anteriores, que não devolviam o Content-Type
        headers: {"Content-Type": "application/pdf", ...requiredHeaders},
        body: file,
    });

    if (!uploadRes.ok) {
        throw new Error(`Upload GCS falhou: ${uploadRes.status}`);
    }
}

export const uploadVolumeFile = uploadSignedFile;
