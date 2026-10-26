package top.egon.cola.component.tianshu.admin.model.dto;

public record DdcResourceMetadataQuery(String producerId, String metadataKind, String resourceType,
        String lifecycle, String permissionCode, String query) { }
