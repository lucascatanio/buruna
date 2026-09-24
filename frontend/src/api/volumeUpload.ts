/**
 * PUT do arquivo de volume direto na URL assinada (upload em 2 fases, ADR-24/ADR-40).
 * Único ponto que faz esse PUT — usado pelas páginas de upload de volume público e
 * privado — para não duplicar o envio dos headers exigidos pela assinatura (FIND-007:
 * o GCS assina `x-goog-content-length-range`, limitando o tamanho aceito pelo PUT).
 */
export async function uploadVolumeFile(
    uploadUrl: string,
    requiredHeaders: Record<string, string>,
    file: File,
): Promise<void> {
    const uploadRes = await fetch(uploadUrl, {
        method: "PUT",
        headers: {
            "Content-Type": "application/pdf",
            ...requiredHeaders,
        },
        body: file,
    });

    if (!uploadRes.ok) {
        throw new Error(`Upload GCS falhou: ${uploadRes.status}`);
    }
}
