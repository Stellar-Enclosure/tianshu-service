package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.Data;

import java.time.Instant;

@Data
public class DdcListSelectionBO {
    private Long id;
    private Long tenantId;
    private Long catalogScopeId;
    private String pageResourceKey;
    private String listKey;
    private String ownerIssuer;
    private String ownerSubject;
    private String ownerDigest;
    private Long templateId;
    private Long version;
    private Instant updatedAt;
}
