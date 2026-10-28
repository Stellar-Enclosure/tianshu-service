package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.Data;

/** One replica snapshot of the immutable scope and its enabled parents. */
@Data
public class DdcMetadataFenceSnapshotBO {
    private Long scopeId;
    private Long bizId;
    private Long bizVersion;
    private Long namespaceId;
    private Long namespaceVersion;
    private Long envId;
    private Long envVersion;
    private Long appId;
    private Long appVersion;
    private Long bindingId;
    private Long bindingVersion;
}
