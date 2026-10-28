package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListSelectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListTemplateBO;

import java.time.Instant;
import java.util.List;

@Mapper
public interface DdcListTemplateMapper {
    List<DdcListTemplateBO> selectAccessible(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId,
            @Param("pageKey") String pageKey, @Param("listKey") String listKey, @Param("ownerDigest") String ownerDigest,
            @Param("issuer") String issuer, @Param("subject") String subject);
    DdcListTemplateBO lockById(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId,
            @Param("pageKey") String pageKey, @Param("listKey") String listKey, @Param("id") Long id);
    List<String> selectColumns(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId, @Param("id") Long id);
    int insertTemplate(@Param("row") DdcListTemplateBO row);
    int updateTemplate(@Param("row") DdcListTemplateBO row, @Param("expectedVersion") Long expectedVersion);
    int clearDefault(@Param("row") DdcListTemplateBO row);
    int deleteColumns(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId, @Param("id") Long id);
    int insertColumn(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId,
            @Param("id") Long id, @Param("key") String key, @Param("sortOrder") int sortOrder);
    int deleteTemplate(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId, @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion, @Param("actor") String actor, @Param("now") Instant now);
    DdcListSelectionBO selectSelection(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId,
            @Param("pageKey") String pageKey, @Param("listKey") String listKey, @Param("ownerDigest") String ownerDigest,
            @Param("issuer") String issuer, @Param("subject") String subject);
    int insertSelection(@Param("row") DdcListSelectionBO row);
    int updateSelection(@Param("row") DdcListSelectionBO row, @Param("expectedVersion") Long expectedVersion);
    int deleteSelection(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId,
            @Param("pageKey") String pageKey, @Param("listKey") String listKey, @Param("ownerDigest") String ownerDigest,
            @Param("issuer") String issuer, @Param("subject") String subject);
}
