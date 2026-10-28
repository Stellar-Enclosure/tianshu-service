package top.egon.cola.component.tianshu.admin.service.list;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcListDefinitionMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListDefinitionBO;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcCatalogActivationService;
import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldPolicy;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** Resolves a list declaration from the exact active producer set and matches the host's implementation. */
@Service("ddcListMetadataService")
@RequiredArgsConstructor
public class DdcListMetadataService {
    @Qualifier("ddcCatalogActivationService") private final DdcCatalogActivationService activations;
    @Qualifier("ddcResourceMetadataProjectionRepository") private final DdcResourceMetadataProjectionRepository resources;
    @Qualifier("ddcListDefinitionMapper") private final DdcListDefinitionMapper definitions;
    @Qualifier("ddcMetadataScopeRepository") private final DdcMetadataScopeRepository scopes;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional(propagation = Propagation.MANDATORY)
    public ListDefinition resolveLocal(SystemAuthorizationSnapshot snapshot, ListDefinitionBinding binding,
            Rbac3StarterProperties.UserScopePolicyProperties scopePolicy, Set<String> userScopes) {
        return resolve(snapshot, binding, scopePolicy, userScopes);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ListDefinition resolveDelegated(SystemAuthorizationSnapshot snapshot, ListDefinitionBinding binding,
            Rbac3StarterProperties.UserScopePolicyProperties scopePolicy, Set<String> userScopes) {
        return resolve(snapshot, binding, scopePolicy, userScopes);
    }

    private ListDefinition resolve(SystemAuthorizationSnapshot snapshot, ListDefinitionBinding binding,
            Rbac3StarterProperties.UserScopePolicyProperties scopePolicy, Set<String> userScopes) {
        String componentKey = binding.componentKey();
        ListDefinition local = binding.definition();
        var view = snapshot.permissionSnapshot();
        if (view == null || componentKey == null || !snapshot.resourceCodes().contains(componentKey)
                || !snapshot.resourceCodes().contains(local.pageResourceKey())) throw unavailable();
        if (!resourceAllowed(activated(snapshot, local.pageResourceKey(), ResourceType.PAGE), snapshot, scopePolicy, userScopes)) throw unavailable();
        var selection = activations.resolve(view.catalogScope(), view.catalogVersion());
        var rows = resources.findActivatedResource(Long.valueOf(view.catalogScope().catalogScopeId()), selection.projectionId(), componentKey);
        if (rows.size() != 1) throw unavailable();
        var row = rows.getFirst();
        var member = selection.members().stream().filter(value -> value.producerId().equals(row.getProducerId())
                && value.sourceId().equals(row.getSourceId()) && value.sourceVersion().equals(row.getSourceVersion())).findFirst().orElseThrow(DdcListMetadataService::unavailable);
        if (!"COMPONENT".equals(row.getResourceType()) || !Set.of("ACTIVE", "DEPRECATED").contains(row.getDefinitionLifecycle())
                || !view.catalogScope().tenantId().equals(row.getTenantId().toString())
                || !view.catalogScope().catalogScopeId().equals(row.getCatalogScopeId().toString())) throw unavailable();
        requireComponentPolicy(componentKey, row.getPolicyJson(), row.getTargetJson(), snapshot, scopePolicy, userScopes);
        ListDefinition resolved = parse(row.getTargetJson());
        requireBinding(row.getTargetJson(), binding, scopePolicy);
        if (!resolved.digest().equals(local.digest()) || !resolved.pageResourceKey().equals(local.pageResourceKey())
                || !resolved.listKey().equals(local.listKey())) throw unavailable();
        var producer = selection.descriptor().requireProducer(member.producerId(), member.producerVersion());
        Long tenant = Long.valueOf(view.catalogScope().tenantId());
        Long scope = Long.valueOf(view.catalogScope().catalogScopeId());
        Long version = Long.valueOf(view.catalogVersion());
        var stored = definitions.find(tenant, scope, version, resolved.pageResourceKey(), resolved.listKey());
        if (stored == null) {
            scopes.lockEnabledTarget(scope).orElseThrow(DdcListMetadataService::unavailable);
            stored = definitions.find(tenant, scope, version, resolved.pageResourceKey(), resolved.listKey());
            if (stored == null) {
                stored = new DdcListDefinitionBO(); stored.setId(ids.nextId(stored)); stored.setTenantId(tenant);
                stored.setCatalogScopeId(scope); stored.setActivationVersion(version); stored.setProducerId(member.producerId());
                stored.setProducerVersion(Long.valueOf(member.producerVersion())); stored.setArtifactDigest(producer.artifactDigest());
                stored.setPageResourceKey(resolved.pageResourceKey()); stored.setListKey(resolved.listKey());
                stored.setDefinitionDigest(resolved.digest()); stored.setDefinitionJson(row.getTargetJson());
                stored.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
                if (definitions.insert(stored) != 1) throw unavailable();
            }
        }
        if (!tenant.equals(stored.getTenantId()) || !scope.equals(stored.getCatalogScopeId())
                || !version.equals(stored.getActivationVersion()) || !member.producerId().equals(stored.getProducerId())
                || !Long.valueOf(member.producerVersion()).equals(stored.getProducerVersion())
                || !producer.artifactDigest().equals(stored.getArtifactDigest()) || !resolved.digest().equals(stored.getDefinitionDigest())
                || !resolved.pageResourceKey().equals(stored.getPageResourceKey()) || !resolved.listKey().equals(stored.getListKey())
                || !row.getTargetJson().equals(stored.getDefinitionJson())) throw unavailable();
        return resolved;
    }

    ListDefinition parse(String json) {
        try {
            JsonNode target = mapper.readTree(json);
            if (target == null || !target.isObject() || !"LIST".equals(target.path("kind").asText())
                    || !keys(target).containsAll(Set.of("kind", "pageResourceKey", "listKey", "definitionDigest", "columns",
                            "pageApiKey", "systemTemplateApiKey", "personalTemplateApiKey", "scopePolicyDigest"))
                    || !Set.of("kind", "pageResourceKey", "listKey", "definitionDigest", "columns", "columns2", "columns3", "columns4",
                            "pageApiKey", "systemTemplateApiKey", "personalTemplateApiKey", "scopePolicyDigest").containsAll(keys(target))) throw unavailable();
            List<String> encoded = new ArrayList<>();
            boolean missing = false;
            for (String group : List.of("columns", "columns2", "columns3", "columns4")) {
                JsonNode chunk = target.get(group);
                if (chunk == null) { missing = true; continue; }
                if (missing || !chunk.isArray() || chunk.isEmpty() || chunk.size() > 64) throw unavailable();
                chunk.forEach(value -> { if (!value.isTextual()) throw unavailable(); encoded.add(value.textValue()); });
            }
            encoded.sort(String::compareTo);
            List<ListDefinition.Column> columns = new ArrayList<>();
            for (int index = 0; index < encoded.size(); index++) {
                String[] parts = encoded.get(index).split("\\|", -1);
                if (parts.length != 7 || !parts[0].equals(String.format(java.util.Locale.ROOT, "%03d", index))
                        || !Set.of("0", "1").contains(parts[5]) || !Set.of("0", "1").contains(parts[6])) throw unavailable();
                columns.add(new ListDefinition.Column(parts[1], Arrays.asList(parts[2].split(",", -1)), parts[3], parts[4],
                        "1".equals(parts[5]), "1".equals(parts[6])));
            }
            var definition = new ListDefinition(target.path("pageResourceKey").asText(), target.path("listKey").asText(), columns);
            if (!definition.digest().equals(target.path("definitionDigest").asText())) throw unavailable();
            return definition;
        } catch (IOException | IllegalArgumentException invalid) { throw unavailable(); }
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> result = new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result;
    }
    private void requireBinding(String json, ListDefinitionBinding binding, Rbac3StarterProperties.UserScopePolicyProperties scopePolicy) {
        try {
            var target = mapper.readTree(json);
            if (!binding.pageApiKey().equals(target.path("pageApiKey").asText())
                    || !binding.systemTemplateApiKey().equals(target.path("systemTemplateApiKey").asText())
                    || !binding.personalTemplateApiKey().equals(target.path("personalTemplateApiKey").asText())
                    || !ListDefinitionBinding.scopePolicyDigest(scopePolicy.isEnabled(), scopePolicy.getPermissionScopes())
                    .equals(target.path("scopePolicyDigest").asText())) throw unavailable();
        } catch (IOException invalid) { throw unavailable(); }
    }
    private void requireComponentPolicy(String componentKey, String json, String target, SystemAuthorizationSnapshot snapshot,
            Rbac3StarterProperties.UserScopePolicyProperties scopeProperties, Set<String> userScopes) {
        try {
            var resource = mapper.readValue(json, PermissionManifest.ResourceDefinition.class);
            var policy = new UserPermissionScopePolicy(scopeProperties);
            if (!componentKey.equals(resource.key()) || resource.type() != ResourceType.COMPONENT
                    || !CanonicalJson.write(resource.target()).equals(target)
                    || !resource.allowedPrincipals().contains(PermissionManifest.PrincipalType.USER)
                    || !policy.explicitScopesAllow(resource, userScopes)
                    || resource.access() != ResourceAccess.PUBLIC && (resource.access() != ResourceAccess.PROTECTED
                    || resource.requirement() == null || !resource.requirement().allows(policy.qualify(resource, snapshot.permissions(), userScopes)))) {
                throw unavailable();
            }
        } catch (IOException | IllegalArgumentException invalid) { throw unavailable(); }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean delegatedApiAllowed(SystemAuthorizationSnapshot snapshot, String key,
            Rbac3StarterProperties.UserScopePolicyProperties scopeProperties, Set<String> userScopes) {
        if (!snapshot.resourceCodes().contains(key)) return false;
        var resource = activated(snapshot, key, ResourceType.API);
        return resourceAllowed(resource, snapshot, scopeProperties, userScopes);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean delegatedFieldReadable(SystemAuthorizationSnapshot snapshot, String key,
            Rbac3StarterProperties.UserScopePolicyProperties scopeProperties, Set<String> userScopes) {
        if (!snapshot.resourceCodes().contains(key)) return false;
        var resource = activated(snapshot, key, ResourceType.FIELD);
        var policy = new UserPermissionScopePolicy(scopeProperties);
        return resource.allowedPrincipals().contains(PermissionManifest.PrincipalType.USER)
                && policy.explicitScopesAllow(resource, userScopes)
                && PermissionFieldPolicy.read(resource, policy.qualify(resource, snapshot.permissions(), userScopes)) != PermissionFieldPolicy.Read.OMIT;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Boolean> delegatedFieldReadability(SystemAuthorizationSnapshot snapshot, Set<String> keys,
            Rbac3StarterProperties.UserScopePolicyProperties scopeProperties, Set<String> userScopes) {
        if (keys == null || keys.size() > 4096) throw unavailable();
        var result = new HashMap<String, Boolean>();
        keys.forEach(key -> result.put(key, false));
        var active = keys.stream().filter(snapshot.resourceCodes()::contains).sorted().toList();
        if (active.isEmpty()) return Map.copyOf(result);
        var view = snapshot.permissionSnapshot();
        if (view == null) throw unavailable();
        var selection = activations.resolve(view.catalogScope(), view.catalogVersion());
        var policy = new UserPermissionScopePolicy(scopeProperties);
        for (int offset = 0; offset < active.size(); offset += 256) {
            var batch = active.subList(offset, Math.min(offset + 256, active.size()));
            var rows = resources.findActivatedResources(Long.valueOf(view.catalogScope().catalogScopeId()), selection.projectionId(), batch);
            var seen = new HashSet<String>();
            for (var row : rows) {
                String key = row.getDefinitionKey();
                if (!batch.contains(key) || !seen.add(key)) throw unavailable();
                var resource = checkedRow(snapshot, selection, row, key, ResourceType.FIELD);
                boolean readable = resource.allowedPrincipals().contains(PermissionManifest.PrincipalType.USER)
                        && policy.explicitScopesAllow(resource, userScopes)
                        && PermissionFieldPolicy.read(resource, policy.qualify(resource, snapshot.permissions(), userScopes)) != PermissionFieldPolicy.Read.OMIT;
                result.put(key, readable);
            }
            if (seen.size() != batch.size()) throw unavailable();
        }
        return Map.copyOf(result);
    }

    private PermissionManifest.ResourceDefinition activated(SystemAuthorizationSnapshot snapshot, String key, ResourceType type) {
        var view = snapshot.permissionSnapshot();
        if (view == null || !snapshot.resourceCodes().contains(key)) throw unavailable();
        var selection = activations.resolve(view.catalogScope(), view.catalogVersion());
        var rows = resources.findActivatedResource(Long.valueOf(view.catalogScope().catalogScopeId()), selection.projectionId(), key);
        if (rows.size() != 1) throw unavailable();
        return checkedRow(snapshot, selection, rows.getFirst(), key, type);
    }

    private PermissionManifest.ResourceDefinition checkedRow(SystemAuthorizationSnapshot snapshot,
            top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogSelectionBO selection,
            top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO row, String key, ResourceType type) {
        var view = snapshot.permissionSnapshot();
        if (!type.name().equals(row.getResourceType()) || !Set.of("ACTIVE", "DEPRECATED").contains(row.getDefinitionLifecycle())
                || !view.catalogScope().tenantId().equals(row.getTenantId().toString())
                || !view.catalogScope().catalogScopeId().equals(row.getCatalogScopeId().toString())
                || selection.members().stream().noneMatch(member -> member.producerId().equals(row.getProducerId())
                && member.sourceId().equals(row.getSourceId()) && member.sourceVersion().equals(row.getSourceVersion()))) throw unavailable();
        try {
            var resource = mapper.readValue(row.getPolicyJson(), PermissionManifest.ResourceDefinition.class);
            if (!key.equals(resource.key()) || resource.type() != type || !CanonicalJson.write(resource.target()).equals(row.getTargetJson())) throw unavailable();
            return resource;
        } catch (IOException | IllegalArgumentException invalid) { throw unavailable(); }
    }

    private static boolean resourceAllowed(PermissionManifest.ResourceDefinition resource, SystemAuthorizationSnapshot snapshot,
            Rbac3StarterProperties.UserScopePolicyProperties scopeProperties, Set<String> userScopes) {
        var policy = new UserPermissionScopePolicy(scopeProperties);
        return resource.allowedPrincipals().contains(PermissionManifest.PrincipalType.USER)
                && policy.explicitScopesAllow(resource, userScopes)
                && (resource.access() == ResourceAccess.PUBLIC || resource.access() == ResourceAccess.PROTECTED
                && resource.requirement() != null && resource.requirement().allows(policy.qualify(resource, snapshot.permissions(), userScopes)));
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("TIANSHU_LIST_METADATA_UNAVAILABLE"); }
}
