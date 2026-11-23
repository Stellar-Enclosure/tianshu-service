package top.egon.cola.component.tianshu.admin.service.list;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;
import org.springframework.util.ReflectionUtils;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogEvent;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRecorder;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcListTemplateMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListSelectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListTemplateBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;
import top.egon.cola.component.tianshu.api.list.ListDelegationResponse;
import top.egon.cola.component.tianshu.api.list.ListTemplateResolver;
import top.egon.cola.component.tianshu.admin.security.management.DdcListDelegationVerifier;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldPolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionContext;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Stores presentation preferences; the current host and field policy remain authoritative. */
@Service("ddcListTemplateService")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcListTemplateService {
    private static final Method SAVE_METHOD = ReflectionUtils.findMethod(DdcListTemplateService.class, "save",
            String.class, String.class, String.class, String.class, String.class, String.class, String.class,
            String.class, List.class, boolean.class);
    private static final Method DELETE_METHOD = ReflectionUtils.findMethod(DdcListTemplateService.class, "delete",
            String.class, String.class, String.class, String.class, String.class, String.class);
    private static final Method DELEGATED_METHOD = ReflectionUtils.findMethod(DdcListTemplateService.class, "executeDelegated",
            DdcListDelegationVerifier.VerifiedDelegation.class, ListDelegationRequest.class, Supplier.class);

    private static final String SYSTEM_OWNER = digest("system");
    private static final int MAX_TEMPLATES = 100;
    private static final int MAX_TEMPLATES_PER_TYPE = 50;

    @Qualifier("ddcListTemplateMapper") private final DdcListTemplateMapper mapper;
    @Qualifier("ddcMetadataScopeRepository") private final DdcMetadataScopeRepository scopes;
    @Qualifier("operationLogRecorder") private final OperationLogRecorder audit;
    @Qualifier("ddcScopeGate") private final DdcScopeGate scopeGate;
    @Qualifier("ddcListMetadataService") private final DdcListMetadataService metadata;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator ids;
    private final CurrentRbac3User users;
    private final PermissionExecutionGuard execution;
    private final PermissionFieldBindingGuard fields;
    private final PermissionPolicyRegistry policies;
    private final List<ListDefinitionBinding> listBindings;

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public View load(String pageKey, String listKey, String expectedCatalogVersion, boolean includeSystemMetadata) {
        return load(context(pageKey, listKey, expectedCatalogVersion, false), includeSystemMetadata);
    }

    private View load(Context context, boolean includeSystemMetadata) {
        String pageKey = context.definition().pageResourceKey(), listKey = context.definition().listKey();
        if (includeSystemMetadata && !context.access().allowed(context.binding().systemTemplateApiKey())) throw new AccessDeniedException("LIST_TEMPLATE_SYSTEM_DENIED");
        Predicate<String> readable = context.access()::readable;
        var rows = accessible(context);
        List<ListTemplateResolver.Template> candidates = rows.stream().map(row -> template(context, row)).toList();
        DdcListSelectionBO selection = mapper.selectSelection(context.tenantId(), context.scopeId(), pageKey, listKey,
                context.ownerDigest(), context.issuer(), context.subject());
        String selectedId = selection == null ? null : selection.getTemplateId().toString();
        var selected = ListTemplateResolver.select(candidates, selectedId, context.issuer(), context.subject());
        List<String> effective = ListTemplateResolver.visible(context.definition(), selected == null ? null : selected.columnKeys(), readable)
                .stream().map(ListDefinition.Column::columnKey).toList();
        List<TemplateView> views = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var template = candidates.get(index);
            List<String> columns = includeSystemMetadata && template.type() == ListTemplateResolver.Template.Type.SYSTEM
                    ? template.columnKeys() : ListTemplateResolver.visible(context.definition(), template.columnKeys(), readable)
                    .stream().map(ListDefinition.Column::columnKey).toList();
            views.add(new TemplateView(row.getId().toString(), row.getTemplateType(), row.getTemplateName(),
                    row.getDefaultTemplate(), row.getVersion().toString(), columns));
        }
        List<ListDefinition.Column> columns = includeSystemMetadata ? context.definition().columns()
                : context.definition().columns().stream().filter(column -> column.fieldKeys().stream().allMatch(readable)).toList();
        boolean mayManageSystem = context.access().allowed(context.binding().systemTemplateApiKey());
        boolean mayManagePersonal = context.access().allowed(context.binding().personalTemplateApiKey());
        recheck(context);
        return new View(context.definition().digest(), context.catalogVersion(), columns, List.copyOf(views),
                selected == null ? null : selected.id(), effective, mayManageSystem, mayManagePersonal);
    }

    @Transactional(timeout = 30)
    public TemplateView save(String pageKey, String listKey, String expectedCatalogVersion, String expectedDefinition,
            String type, String id, String expectedVersion, String name, List<String> requestedColumns, boolean defaultTemplate) {
        return save(context(pageKey, listKey, expectedCatalogVersion, true), expectedDefinition, type, id, expectedVersion, name, requestedColumns, defaultTemplate, SAVE_METHOD, System.nanoTime());
    }

    private TemplateView save(Context context, String expectedDefinition, String type, String id, String expectedVersion,
            String name, List<String> requestedColumns, boolean defaultTemplate, Method method, long started) {
        String pageKey = context.definition().pageResourceKey(), listKey = context.definition().listKey();
        requireDefinition(context, expectedDefinition);
        boolean system = "SYSTEM".equals(type);
        if (!system && !"PERSONAL".equals(type)) throw invalid();
        if (!context.access().allowed(system ? context.binding().systemTemplateApiKey() : context.binding().personalTemplateApiKey())) throw new AccessDeniedException("LIST_TEMPLATE_EDIT_DENIED");
        if (name == null || name.isBlank() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)) throw invalid();
        if (requestedColumns == null || requestedColumns.size() > 256) throw invalid();
        DdcListTemplateBO row;
        List<String> prior = List.of();
        if (id == null) {
            if (expectedVersion != null || accessible(context).stream().filter(value -> type.equals(value.getTemplateType())).count()
                    >= MAX_TEMPLATES_PER_TYPE) throw invalid();
            row = new DdcListTemplateBO();
            row.setId(ids.nextId(row)); row.setTenantId(context.tenantId()); row.setCatalogScopeId(context.scopeId());
            row.setPageResourceKey(pageKey); row.setListKey(listKey); row.setTemplateType(type);
            row.setOwnerIssuer(system ? "" : context.issuer()); row.setOwnerSubject(system ? "" : context.subject());
            row.setOwnerDigest(system ? SYSTEM_OWNER : context.ownerDigest()); row.setVersion(0L);
            row.setCreatedBy(context.subject()); row.setCreatedAt(now());
        } else {
            row = owned(context, positive(id), system);
            if (expectedVersion == null || row.getVersion() != nonNegative(expectedVersion)) throw conflict();
            prior = mapper.selectColumns(context.tenantId(), context.scopeId(), row.getId());
        }
        Predicate<String> readable = context.access()::readable;
        List<String> columns;
        if (system) {
            var allowed = context.definition().columns().stream().map(ListDefinition.Column::columnKey).toList();
            if (requestedColumns.stream().distinct().count() != requestedColumns.size() || !allowed.containsAll(requestedColumns)) throw invalid();
            columns = List.copyOf(requestedColumns);
        } else {
            columns = ListTemplateResolver.mergePersonal(context.definition(), prior, requestedColumns, readable);
        }
        row.setTemplateName(name); row.setDefaultTemplate(defaultTemplate);
        row.setUpdatedBy(context.subject()); row.setUpdatedAt(now());
        recheck(context);
        if (defaultTemplate) mapper.clearDefault(row);
        if (id == null) {
            if (mapper.insertTemplate(row) != 1) throw conflict();
        } else {
            if (mapper.updateTemplate(row, row.getVersion()) != 1) throw conflict();
            row.setVersion(row.getVersion() + 1);
            mapper.deleteColumns(context.tenantId(), context.scopeId(), row.getId());
        }
        for (int index = 0; index < columns.size(); index++) {
            if (mapper.insertColumn(context.tenantId(), context.scopeId(), row.getId(), columns.get(index), index) != 1) throw conflict();
        }
        recheck(context);
        audit(context, id == null ? "TIANSHU_LIST_TEMPLATE_CREATE" : "TIANSHU_LIST_TEMPLATE_UPDATE", row, method, started);
        List<String> visible = system ? columns : ListTemplateResolver.visible(context.definition(), columns, readable)
                .stream().map(ListDefinition.Column::columnKey).toList();
        return new TemplateView(row.getId().toString(), type, name, defaultTemplate, row.getVersion().toString(), visible);
    }

    @Transactional(timeout = 30)
    public void select(String pageKey, String listKey, String expectedCatalogVersion, String expectedDefinition, String id) {
        select(context(pageKey, listKey, expectedCatalogVersion, true), expectedDefinition, id);
    }

    private void select(Context context, String expectedDefinition, String id) {
        String pageKey = context.definition().pageResourceKey(), listKey = context.definition().listKey();
        requireDefinition(context, expectedDefinition);
        Long templateId = positive(id);
        if (accessible(context).stream().noneMatch(row -> templateId.equals(row.getId()))) throw missing();
        var selected = mapper.selectSelection(context.tenantId(), context.scopeId(), pageKey, listKey,
                context.ownerDigest(), context.issuer(), context.subject());
        recheck(context);
        if (selected == null) {
            selected = new DdcListSelectionBO(); selected.setId(ids.nextId(selected)); selected.setTenantId(context.tenantId());
            selected.setCatalogScopeId(context.scopeId()); selected.setPageResourceKey(pageKey); selected.setListKey(listKey);
            selected.setOwnerIssuer(context.issuer()); selected.setOwnerSubject(context.subject()); selected.setOwnerDigest(context.ownerDigest());
            selected.setTemplateId(templateId); selected.setVersion(0L); selected.setUpdatedAt(now());
            if (mapper.insertSelection(selected) != 1) throw conflict();
        } else {
            selected.setTemplateId(templateId); selected.setUpdatedAt(now());
            if (mapper.updateSelection(selected, selected.getVersion()) != 1) throw conflict();
        }
        recheck(context);
    }

    @Transactional(timeout = 30)
    public void delete(String pageKey, String listKey, String expectedCatalogVersion, String type, String id, String version) {
        delete(context(pageKey, listKey, expectedCatalogVersion, true), type, id, version, DELETE_METHOD, System.nanoTime());
    }

    private void delete(Context context, String type, String id, String version, Method method, long started) {
        boolean system = "SYSTEM".equals(type);
        if (!system && !"PERSONAL".equals(type)) throw invalid();
        if (!context.access().allowed(system ? context.binding().systemTemplateApiKey() : context.binding().personalTemplateApiKey())) throw new AccessDeniedException("LIST_TEMPLATE_EDIT_DENIED");
        var row = owned(context, positive(id), system);
        long expected = nonNegative(version);
        if (row.getVersion() != expected) throw conflict();
        recheck(context);
        if (mapper.deleteTemplate(context.tenantId(), context.scopeId(), row.getId(), expected, context.subject(), now()) != 1) throw conflict();
        recheck(context);
        audit(context, "TIANSHU_LIST_TEMPLATE_DELETE", row, method, started);
    }

    private Context context(String pageKey, String listKey, String expectedVersion, boolean lock) {
        var matches = listBindings.stream().filter(binding -> binding.definition().pageResourceKey().equals(pageKey)
                && binding.definition().listKey().equals(listKey)).toList();
        if (matches.size() != 1) throw missing();
        var binding = matches.getFirst();
        ListDefinition definition = binding.definition();
        var user = users.require();
        var policySnapshot = copyPolicy(execution.userScopePolicy());
        if (!policyDigest(execution.userScopePolicy()).equals(policyDigest(fields.userScopePolicy()))) throw conflict();
        var userScopes = UserPermissionScopePolicy.currentScopes();
        var view = user.snapshot().permissionSnapshot();
        if (view == null || !view.catalogVersion().equals(expectedVersion) || !allowed(user, binding.pageApiKey(), policySnapshot, userScopes)) throw conflict();
        CatalogScope scope = view.catalogScope();
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        if (!tenantId.toString().equals(scope.tenantId()) || !"tianshu".equals(scope.applicationCode())) throw missing();
        Long scopeId = positive(scope.catalogScopeId());
        DdcMetadataScopeBO target = (lock ? scopes.lockEnabledTarget(scopeId) : scopes.findByCatalogScopeId(scopeId)).orElseThrow(DdcListTemplateService::missing);
        if (!scope.equals(new CatalogScope(target.getCatalogScopeId().toString(), target.getTenantId().toString(),
                target.getBizCode(), target.getAppCode(), target.getNamespaceCode(), target.getEnvCode()))) throw missing();
        scopeGate.assertEnabledFresh(scope.businessCode(), scope.applicationCode(), scope.environment(), scope.namespaceCode());
        String issuer = view.issuer(), subject = view.subject();
        ListDefinition activeDefinition = metadata.resolveLocal(user.snapshot(), binding, policySnapshot, userScopes);
        String initialPolicy = ListDefinitionBinding.scopePolicyDigest(policySnapshot.isEnabled(), policySnapshot.getPermissionScopes());
        Access access = new Access() {
            @Override public boolean allowed(String key) { return DdcListTemplateService.this.allowed(user, key, policySnapshot, userScopes); }
            @Override public boolean readable(String key) { return DdcListTemplateService.this.readable(user, policySnapshot, userScopes).test(key); }
            @Override public void recheck() {
                requireSame(user.snapshot(), execution.require(PermissionExecutionContext.currentApiKey()).snapshot());
                if (!initialPolicy.equals(policyDigest(execution.userScopePolicy())) || !initialPolicy.equals(policyDigest(fields.userScopePolicy()))
                        || !userScopes.equals(UserPermissionScopePolicy.currentScopes())) throw conflict();
            }
        };
        return new Context(scope, tenantId, scopeId, view.catalogVersion(), issuer, subject,
                digest(issuer + "\u0000" + subject), activeDefinition, binding, access);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public ListDelegationResponse executeDelegated(DdcListDelegationVerifier.VerifiedDelegation verified, ListDelegationRequest request,
            Supplier<DdcListDelegationVerifier.VerifiedDelegation> refresh) {
        long started = System.nanoTime();
        Context context = delegatedContext(verified, request, request.operation() != ListDelegationRequest.Operation.LOAD, refresh);
        String digest = context.definition().digest();
        return switch (request.operation()) {
            case LOAD -> response(load(context, request.includeSystemMetadata()));
            case SAVE_SYSTEM, SAVE_PERSONAL -> {
                String type = request.operation() == ListDelegationRequest.Operation.SAVE_SYSTEM ? "SYSTEM" : "PERSONAL";
                var saved = save(context, digest, type, request.templateId(), request.expectedVersion(), request.name(),
                        request.columnKeys(), Boolean.TRUE.equals(request.defaultTemplate()), DELEGATED_METHOD, started);
                yield response(context, saved);
            }
            case SELECT -> { select(context, digest, request.templateId()); yield response(context, null); }
            case DELETE_SYSTEM, DELETE_PERSONAL -> {
                String type = request.operation() == ListDelegationRequest.Operation.DELETE_SYSTEM ? "SYSTEM" : "PERSONAL";
                delete(context, type, request.templateId(), request.expectedVersion(), DELEGATED_METHOD, started); yield response(context, null);
            }
        };
    }

    private Context delegatedContext(DdcListDelegationVerifier.VerifiedDelegation verified, ListDelegationRequest request,
            boolean lock, Supplier<DdcListDelegationVerifier.VerifiedDelegation> refresh) {
        Objects.requireNonNull(verified, "verified delegation"); Objects.requireNonNull(request, "delegation request");
        Objects.requireNonNull(refresh, "delegation refresh");
        var snapshot = verified.userSnapshot(); var view = snapshot.permissionSnapshot();
        if (view == null || !view.catalogVersion().equals(request.catalogVersion()) || !view.authVersion().equals(request.authVersion())
                || !view.policyVersion().equals(request.policyVersion())
                || !view.activeRoleContext().rootRoleIds().equals(request.activeRootRoleIds())) throw conflict();
        var policy = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true).setPermissionScopes(request.permissionScopes());
        var binding = request.binding();
        CatalogScope scope = view.catalogScope(); Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        if (!tenantId.toString().equals(scope.tenantId())) throw missing();
        Long scopeId = positive(scope.catalogScopeId());
        DdcMetadataScopeBO target = (lock ? scopes.lockEnabledTarget(scopeId) : scopes.findByCatalogScopeId(scopeId)).orElseThrow(DdcListTemplateService::missing);
        if (!scope.equals(new CatalogScope(target.getCatalogScopeId().toString(), target.getTenantId().toString(),
                target.getBizCode(), target.getAppCode(), target.getNamespaceCode(), target.getEnvCode()))) throw missing();
        scopeGate.assertEnabledFresh(scope.businessCode(), scope.applicationCode(), scope.environment(), scope.namespaceCode());
        var definition = metadata.resolveDelegated(snapshot, binding, policy, verified.userScopes());
        var fieldKeys = definition.columns().stream().flatMap(column -> column.fieldKeys().stream()).collect(java.util.stream.Collectors.toSet());
        var readableFields = metadata.delegatedFieldReadability(snapshot, fieldKeys, policy, verified.userScopes());
        var allowedCache = new java.util.HashMap<String, Boolean>();
        Access access = new Access() {
            @Override public boolean allowed(String key) { return allowedCache.computeIfAbsent(key,
                    value -> metadata.delegatedApiAllowed(snapshot, value, policy, verified.userScopes())); }
            @Override public boolean readable(String key) { return Boolean.TRUE.equals(readableFields.get(key)); }
            @Override public void recheck() {
                var current = refresh.get();
                if (current == null || !verified.service().equals(current.service()) || !verified.issuer().equals(current.issuer())
                        || !verified.userScopes().equals(current.userScopes())) throw conflict();
                requireSame(snapshot, current.userSnapshot());
            }
        };
        if (!access.allowed(binding.pageApiKey())) throw new AccessDeniedException("LIST_TEMPLATE_PAGE_DENIED");
        return new Context(scope, tenantId, scopeId, view.catalogVersion(), view.issuer(), view.subject(),
                digest(view.issuer() + "\u0000" + view.subject()), definition, binding, access);
    }

    private static ListDelegationResponse response(View view) {
        return new ListDelegationResponse(view.definitionDigest(), view.catalogVersion(), view.columns().stream()
                .map(column -> new ListDelegationResponse.Column(column.columnKey(), column.labelPage(), column.labelKey())).toList(),
                view.templates().stream().map(DdcListTemplateService::response).toList(), view.selectedTemplateId(), view.visibleColumns(),
                view.mayManageSystem(), view.mayManagePersonal(), null);
    }
    private static ListDelegationResponse response(Context context, TemplateView saved) {
        return new ListDelegationResponse(context.definition().digest(), context.catalogVersion(), List.of(), List.of(), null, List.of(), false, false,
                saved == null ? null : response(saved));
    }
    private static ListDelegationResponse.Template response(TemplateView value) {
        return new ListDelegationResponse.Template(value.id(), value.type(), value.name(), value.defaultTemplate(), value.version(), value.columnKeys());
    }

    private List<DdcListTemplateBO> accessible(Context context) {
        List<DdcListTemplateBO> result = mapper.selectAccessible(context.tenantId(), context.scopeId(),
                context.definition().pageResourceKey(), context.definition().listKey(), context.ownerDigest(), context.issuer(), context.subject());
        if (result.size() > MAX_TEMPLATES) throw new IllegalStateException("LIST_TEMPLATE_LIMIT_EXCEEDED");
        return result;
    }

    private DdcListTemplateBO owned(Context context, Long id, boolean system) {
        var row = mapper.lockById(context.tenantId(), context.scopeId(), context.definition().pageResourceKey(), context.definition().listKey(), id);
        if (row == null || !row.getTemplateType().equals(system ? "SYSTEM" : "PERSONAL")
                || !system && (!context.issuer().equals(row.getOwnerIssuer()) || !context.subject().equals(row.getOwnerSubject())
                || !context.ownerDigest().equals(row.getOwnerDigest()))) throw missing();
        return row;
    }

    private ListTemplateResolver.Template template(Context context, DdcListTemplateBO row) {
        boolean system = "SYSTEM".equals(row.getTemplateType());
        return new ListTemplateResolver.Template(row.getId().toString(), system ? ListTemplateResolver.Template.Type.SYSTEM
                : ListTemplateResolver.Template.Type.PERSONAL, system ? null : row.getOwnerIssuer(), system ? null : row.getOwnerSubject(),
                row.getDefaultTemplate(), mapper.selectColumns(context.tenantId(), context.scopeId(), row.getId()));
    }

    private boolean allowed(Rbac3UserDetails user, String apiKey, Rbac3StarterProperties.UserScopePolicyProperties policyProperties, java.util.Set<String> scopes) {
        try {
            var resource = policies.require(apiKey, ResourceType.API);
            policies.requireActive(user.snapshot(), apiKey);
            var scopePolicy = new UserPermissionScopePolicy(policyProperties);
            return resource.allowedPrincipals().contains(PermissionManifest.PrincipalType.USER)
                    && scopePolicy.explicitScopesAllow(resource, scopes)
                    && (resource.access() == ResourceAccess.PUBLIC || resource.access() == ResourceAccess.PROTECTED
                    && resource.requirement() != null && resource.requirement().allows(scopePolicy.qualify(resource, user.permissions(), scopes)));
        } catch (Rbac3AuthorizationClient.AuthorizationDeniedException denied) { return false; }
    }

    private Predicate<String> readable(Rbac3UserDetails user, Rbac3StarterProperties.UserScopePolicyProperties policyProperties, java.util.Set<String> scopes) {
        var scopePolicy = new UserPermissionScopePolicy(policyProperties);
        return key -> {
            try {
                var field = policies.require(key, ResourceType.FIELD);
                policies.requireActive(user.snapshot(), key);
                return field.allowedPrincipals().contains(PermissionManifest.PrincipalType.USER)
                        && scopePolicy.explicitScopesAllow(field, scopes)
                        && PermissionFieldPolicy.read(field, scopePolicy.qualify(field, user.permissions(), scopes)) != PermissionFieldPolicy.Read.OMIT;
            } catch (Rbac3AuthorizationClient.AuthorizationDeniedException denied) { return false; }
        };
    }

    private static Rbac3StarterProperties.UserScopePolicyProperties copyPolicy(UserPermissionScopePolicy source) {
        var values = new java.util.LinkedHashMap<String, java.util.Set<String>>();
        source.permissionScopes().forEach((key, value) -> values.put(key, java.util.Set.copyOf(value)));
        return new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(source.enabled()).setPermissionScopes(java.util.Map.copyOf(values));
    }
    private static String policyDigest(UserPermissionScopePolicy policy) { return ListDefinitionBinding.scopePolicyDigest(policy.enabled(), policy.permissionScopes()); }

    private void recheck(Context context) { context.access().recheck(); }

    private static void requireSame(SystemAuthorizationSnapshot before, SystemAuthorizationSnapshot after) {
        var oldView = before.permissionSnapshot();
        var newView = after.permissionSnapshot();
        if (oldView == null || newView == null || !oldView.issuer().equals(newView.issuer())
                || !oldView.subject().equals(newView.subject()) || !oldView.catalogScope().equals(newView.catalogScope())
                || !oldView.catalogVersion().equals(newView.catalogVersion()) || !oldView.authVersion().equals(newView.authVersion())
                || !oldView.policyVersion().equals(newView.policyVersion()) || !oldView.activeRoleContext().equals(newView.activeRoleContext())
                || !oldView.permissionCodes().equals(newView.permissionCodes()) || !before.resourceCodes().equals(after.resourceCodes())
                || !Objects.equals(before.policyBinding(), after.policyBinding())) throw conflict();
    }
    private void audit(Context context, String action, DdcListTemplateBO row, Method method, long started) {
        CatalogScope scope = context.scope();
        var details = JsonNodeFactory.instance.objectNode().put("templateId", row.getId().toString())
                .put("type", row.getTemplateType()).put("version", row.getVersion().toString());
        audit.recordInTransaction(OperationLogEvent.builder()
                .moduleCode("LIST").actionCode(action).resourceType("LIST_TEMPLATE").resourceId(row.getId().toString())
                .bizCode(scope.businessCode()).appCode(scope.applicationCode()).env(scope.environment())
                .namespaceCode(scope.namespaceCode()).phase(OperationLogPhaseEnum.COMMAND)
                .outcome(OperationLogOutcomeEnum.SUCCEEDED)
                .durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).details(details).build(), method);
    }
    private static void requireDefinition(Context context, String digest) { if (!context.definition().digest().equals(digest)) throw conflict(); }
    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    private static long positive(String value) { long result = nonNegative(value); if (result == 0) throw invalid(); return result; }
    private static long nonNegative(String value) {
        try { if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid(); return Long.parseLong(value); }
        catch (NumberFormatException failure) { throw invalid(); }
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static CommonException invalid() { return new CommonException(56401, "TIANSHU_LIST_TEMPLATE_INVALID", "Invalid list template"); }
    private static CommonException missing() { return new CommonException(56402, "TIANSHU_LIST_TEMPLATE_NOT_FOUND", "List or template not found"); }
    private static CommonException conflict() { return new CommonException(56404, "TIANSHU_LIST_TEMPLATE_CONFLICT", "List or template changed; reload and retry"); }

    private interface Access { boolean allowed(String key); boolean readable(String key); void recheck(); }
    private record Context(CatalogScope scope, Long tenantId, Long scopeId, String catalogVersion, String issuer, String subject,
                           String ownerDigest, ListDefinition definition, ListDefinitionBinding binding, Access access) { }
    public record TemplateView(String id, String type, String name, boolean defaultTemplate, String version, List<String> columnKeys) { }
    public record View(String definitionDigest, String catalogVersion, List<ListDefinition.Column> columns,
                       List<TemplateView> templates, String selectedTemplateId, List<String> visibleColumns,
                       boolean mayManageSystem, boolean mayManagePersonal) { }
}
