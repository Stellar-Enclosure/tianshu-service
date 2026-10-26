package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProducerBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProjectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogSelectionBO;
import top.egon.cola.component.tianshu.admin.repository.DdcCatalogActivationProducerRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcCatalogActivationProjectionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects a single authenticated descriptor and all of its imported members in one local transaction. */
@Service("ddcCatalogActivationService")
@RequiredArgsConstructor
public class DdcCatalogActivationService {
    @Qualifier("ddcCatalogActivationClient") private final DdcCatalogActivationClient client;
    @Qualifier("ddcCatalogActivationJson") private final DdcCatalogActivationJson json;
    @Qualifier("ddcCatalogActivationProjectionRepository") private final DdcCatalogActivationProjectionRepository projections;
    @Qualifier("ddcCatalogActivationProducerRepository") private final DdcCatalogActivationProducerRepository producers;
    @Qualifier("ddcMetadataScopeRepository") private final DdcMetadataScopeRepository scopes;
    @Qualifier("ddcI18nSourceVersionRepository") private final DdcI18nSourceVersionRepository sources;
    @Qualifier("ddcMetadataTransactionTemplate") private final TransactionTemplate transaction;

    public DdcCatalogSelectionBO resolve(CatalogScope scope, String version) {
        if (!EgonColaTenantIdProvider.currentTenantId().toString().equals(scope.tenantId())) throw DdcCatalogActivationClient.unavailable();
        long activationVersion = positive(version);
        Long scopeId = Long.valueOf(scope.catalogScopeId());
        var existing = projections.findByVersion(scopeId, activationVersion);
        if (existing.isPresent()) return selection(scope, version, existing.get());
        ActivationDescriptor fetched = client.fetch(scope, version);
        return Objects.requireNonNull(transaction.execute(status -> {
            var target = scopes.lockEnabledTarget(scopeId).orElseThrow(DdcCatalogActivationClient::unavailable);
            CatalogScope actual = new CatalogScope(target.getCatalogScopeId().toString(), target.getTenantId().toString(),
                    target.getBizCode(), target.getAppCode(), target.getNamespaceCode(), target.getEnvCode());
            if (!scope.equals(actual)) throw DdcCatalogActivationClient.unavailable();
            var duplicate = projections.findByVersion(scopeId, activationVersion);
            if (duplicate.isPresent()) {
                if (!fetched.digest().equals(duplicate.get().getDescriptorDigest())) throw DdcCatalogActivationClient.unavailable();
                return selection(scope, version, duplicate.get());
            }
            List<DdcCatalogActivationProducerBO> members = new ArrayList<>();
            for (var producer : fetched.producers()) {
                var source = sources.findByVersion(scopeId, producer.producerId(), producer.sourceVersion())
                        .orElseThrow(DdcCatalogActivationClient::unavailable);
                if (!source.getManifestDigest().equals(producer.digests().manifestDigest())
                        || !source.getPolicyDigest().equals(producer.digests().policyDigest())
                        || !source.getMetadataDigest().equals(producer.digests().metadataDigest())) throw DdcCatalogActivationClient.unavailable();
                members.add(DdcCatalogActivationProducerBO.builder().catalogScopeId(scopeId).producerId(producer.producerId())
                        .producerVersion(Long.valueOf(producer.producerVersion())).sourceId(source.getId()).sourceVersion(producer.sourceVersion())
                        .policyDigest(producer.digests().policyDigest()).metadataDigest(producer.digests().metadataDigest()).build());
            }
            var stored = projections.insert(DdcCatalogActivationProjectionBO.builder().catalogScopeId(scopeId).activationVersion(activationVersion)
                    .descriptorDigest(fetched.digest()).descriptorJson(fetched.canonicalize()).producerCount(members.size())
                    .authorityResourceUri(client.authorityResource(scope)).build());
            members.forEach(member -> member.setActivationProjectionId(stored.getId()));
            producers.insertAll(members);
            if (producers.countSelectionConflicts(scopeId, stored.getId()) != 0) throw DdcCatalogActivationClient.unavailable();
            return selection(scope, version, stored);
        }));
    }

    private DdcCatalogSelectionBO selection(CatalogScope scope, String version, DdcCatalogActivationProjectionBO stored) {
        ActivationDescriptor descriptor = json.read(stored.getDescriptorJson());
        if (!scope.equals(descriptor.scope()) || !version.equals(descriptor.activationVersion())
                || !descriptor.digest().equals(stored.getDescriptorDigest()) || descriptor.producers().size() != stored.getProducerCount()
                || !client.authorityResource(scope).equals(stored.getAuthorityResourceUri())) throw DdcCatalogActivationClient.unavailable();
        var members = producers.findByProjection(Long.valueOf(scope.catalogScopeId()), stored.getId());
        if (members.size() != descriptor.producers().size()) throw DdcCatalogActivationClient.unavailable();
        List<DdcCatalogSelectionBO.Member> selection = new ArrayList<>();
        for (var member : members) {
            var expected = descriptor.requireProducer(member.getProducerId(), member.getProducerVersion().toString());
            if (!member.getSourceVersion().equals(expected.sourceVersion()) || !member.getPolicyDigest().equals(expected.digests().policyDigest())
                    || !member.getMetadataDigest().equals(expected.digests().metadataDigest())) throw DdcCatalogActivationClient.unavailable();
            selection.add(new DdcCatalogSelectionBO.Member(member.getProducerId(), member.getProducerVersion().toString(), member.getSourceVersion(), member.getSourceId()));
        }
        return new DdcCatalogSelectionBO(stored.getId(), descriptor, selection);
    }

    static long positive(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}") || Long.parseLong(value) <= 0) throw new IllegalArgumentException();
            return Long.parseLong(value);
        } catch (IllegalArgumentException invalid) { throw DdcI18nValidation.invalid("Invalid activation version"); }
    }
}
