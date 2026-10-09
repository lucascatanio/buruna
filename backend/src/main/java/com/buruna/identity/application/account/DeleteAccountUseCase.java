package com.buruna.identity.application.account;

import com.buruna.work.application.maintenance.DeletePrivateCollectionForUserUseCase;
import com.buruna.shared.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Remove a conta a pedido do dono: confirma senha (e 2FA), apaga a coleção privada e
 * anonimiza o usuário. A linha não é apagada porque mangás públicos e volumes enviados ao
 * catálogo continuam referenciando o id (FKs {@code RESTRICT}); eles ficam intactos.
 *
 * <p>Sem {@code @Transactional} aqui, como no {@code RunInactivityUseCase}: a coleção
 * privada é apagada na transação do use case de {@code work}, a anonimização na sua, e o
 * storage fica fora de qualquer transação, best-effort. A coleção sai antes da
 * anonimização para que uma falha no meio deixe a conta ainda utilizável, e o pedido possa
 * ser repetido, em vez de uma conta anonimizada com coleção órfã.
 */
@Service
public class DeleteAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteAccountUseCase.class);

    private final AccountService accountService;
    private final DeletePrivateCollectionForUserUseCase deletePrivateCollectionForUser;
    private final StorageClient storageClient;

    public DeleteAccountUseCase(AccountService accountService,
                                DeletePrivateCollectionForUserUseCase deletePrivateCollectionForUser,
                                StorageClient storageClient) {
        this.accountService = accountService;
        this.deletePrivateCollectionForUser = deletePrivateCollectionForUser;
        this.storageClient = storageClient;
    }

    public void handle(UUID userId, String password, String totpCode) {
        accountService.confirmOwnership(userId, password, totpCode);

        List<String> objectNames = new ArrayList<>(deletePrivateCollectionForUser.handle(userId));
        accountService.anonymize(userId).ifPresent(objectNames::add);

        for (String objectName : objectNames) {
            try {
                storageClient.delete(objectName);
            } catch (RuntimeException e) {
                log.warn("Failed to delete storage object {} (best-effort): {}", objectName, e.getMessage());
            }
        }
    }
}
