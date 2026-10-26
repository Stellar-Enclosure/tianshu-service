package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nImportReceiptBO;
import java.util.Optional;

public interface DdcI18nImportReceiptRepository {
    Optional<DdcI18nImportReceiptBO> findByOperation(Long catalogScopeId, String producerId, String operationId);
    Long nextReceiptId();
    DdcI18nImportReceiptBO insert(DdcI18nImportReceiptBO command);
}
