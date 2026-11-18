package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.*;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigProcessReceiptPO;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;

/** Explicit report projection and PO/BO conversion; untrusted commands have no identity mapping. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcConfigProcessReceiptConverter extends BaseConverter<DdcConfigProcessReceiptPO, DdcConfigProcessReceiptBO> {
    @Override
    @Mapping(target = "bootFence", ignore = true)
    @Mapping(target = "authorizedManifest", ignore = true)
    @Mapping(target = "reasonMessage", ignore = true)
    DdcConfigProcessReceiptBO toTarget(DdcConfigProcessReceiptPO source);

    /** Retains the received fingerprint input only until validation; it is not a durable projection. */
    DdcConfigProcessReceiptBO copyAuthorized(DdcConfigProcessReceiptBO source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "configRevision", source = "releaseRevision")
    @Mapping(target = "bootId", source = "bootId")
    @Mapping(target = "reportSeq", source = "reportSeq")
    @Mapping(target = "appliedRevision", source = "appliedRevision")
    @Mapping(target = "contentDigest", source = "contentSha256", qualifiedByName = "rawDigest")
    @Mapping(target = "appliedDigest", source = "appliedContentSha256", qualifiedByName = "rawDigest")
    @Mapping(target = "restartDigest", source = "baselineRestartDigest", qualifiedByName = "rawDigest")
    @Mapping(target = "receiptDigest", source = "receiptDigest", qualifiedByName = "rawDigest")
    @Mapping(target = "result", source = "status")
    @Mapping(target = "reasonCode", source = "errorCode")
    @Mapping(target = "reasonMessage", source = "errorMessage")
    @Mapping(target = "reportedAt", source = "reportedAt")
    @Mapping(target = "appliedAt", source = "appliedAt")
    DdcConfigProcessReceiptBO fromCommand(DdcConfigReceiptCommand source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "schemaVersion", constant = "1")
    @Mapping(target = "bootId", source = "bootId")
    @Mapping(target = "releaseRevision", source = "configRevision")
    @Mapping(target = "reportSeq", source = "reportSeq")
    @Mapping(target = "appliedRevision", source = "appliedRevision")
    @Mapping(target = "contentSha256", source = "contentDigest", qualifiedByName = "wireDigest")
    @Mapping(target = "appliedContentSha256", source = "appliedDigest", qualifiedByName = "wireDigest")
    @Mapping(target = "baselineRestartDigest", source = "restartDigest", qualifiedByName = "wireDigest")
    @Mapping(target = "receiptDigest", source = "receiptDigest", qualifiedByName = "wireDigest")
    @Mapping(target = "status", source = "result")
    @Mapping(target = "errorCode", source = "reasonCode")
    @Mapping(target = "errorMessage", source = "reasonMessage")
    @Mapping(target = "reportedAt", source = "reportedAt")
    @Mapping(target = "appliedAt", source = "appliedAt")
    DdcConfigReceiptCommand toCommand(DdcConfigProcessReceiptBO source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "bootFence")
    @Mapping(target = "clusterId", source = "clusterId")
    @Mapping(target = "podUid", source = "podUid")
    @Mapping(target = "containerName", source = "containerName")
    @Mapping(target = "containerId", source = "containerId")
    @Mapping(target = "bootId", source = "bootId")
    @Mapping(target = "workloadUid", source = "workloadUid")
    @Mapping(target = "k8sNamespace", source = "k8sNamespace")
    DdcConfigBootBindingBO toBootBinding(DdcConfigProcessReceiptBO source);

    @Named("rawDigest")
    default String rawDigest(String value) {
        if (value == null) return null;
        if (!value.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("CONFIG_RECEIPT_DIGEST_INVALID");
        return value.substring(7);
    }
    @Named("wireDigest")
    default String wireDigest(String value) { return value == null ? null : "sha256:" + value; }
    default String statusCode(DdcConfigReceiptStatusEnum value) { return value == null ? null : value.getCode(); }
    default DdcConfigReceiptStatusEnum receiptStatus(String value) { return value == null ? null : DdcConfigReceiptStatusEnum.fromCode(value); }
}
