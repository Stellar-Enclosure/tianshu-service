package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceVersionBO;
import java.util.Optional;

public interface DdcI18nSourceVersionRepository {
    Optional<DdcI18nSourceVersionBO> findByVersion(Long catalogScopeId, String producerId, String sourceVersion);
    DdcI18nSourceVersionBO insert(DdcI18nSourceVersionBO command);
}
