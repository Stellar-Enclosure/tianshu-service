package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.Data;

import java.time.Instant;

@Data
public class DdcListTemplateBO {
    private Long id;
    private Long tenantId;
    private Long catalogScopeId;
    private String pageResourceKey;
    private String listKey;
    private String templateType;
    private String ownerIssuer;
    private String ownerSubject;
    private String ownerDigest;
    private String templateName;
    private Boolean defaultTemplate;
    private Long version;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private Instant deletedAt;
}
