package top.egon.cola.component.tianshu.admin.service.config;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigProcessReceiptConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.util.Objects;

/** Admits one report using independently verified workload and signed fixed-release authority. */
@Slf4j
@Service("ddcConfigReceiptService")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnProperty(prefix = "egon.cola.component.tianshu.admin.config-delivery", name = "enabled", havingValue = "true")
public class DdcConfigReceiptService {
    @Qualifier("ddcConfigSnapshotService") private final DdcConfigSnapshotService snapshots;
    @Qualifier("ddcConfigDeliveryTokenService") private final DdcConfigDeliveryTokenService tokens;
    @Qualifier("ddcConfigProcessReceiptRepository") private final DdcConfigProcessReceiptRepository receipts;
    @Qualifier("ddcConfigProcessReceiptConverterImpl") private final DdcConfigProcessReceiptConverter converter;
    @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor tenantContext;
    @Qualifier("defaultValidator") private final Validator validator;

    public void accept(@NotNull @Valid DdcConfigWorkloadIdentityVO workload,
            @NotNull @Valid DdcConfigReceiptCommand command, @NotBlank @Size(max = 1024) String idempotencyKey) {
        try {
            new ValidationUtils(validator).validate(workload);
            new ValidationUtils(validator).validate(command);
        } catch (RuntimeException invalid) {
            throw rejected(HttpStatus.BAD_REQUEST, "CONFIG_RECEIPT_INVALID");
        }
        if (idempotencyKey == null || idempotencyKey.length() > 1024) throw rejected(HttpStatus.BAD_REQUEST, "CONFIG_RECEIPT_INVALID");
        try {
            tenantContext.run(workload.tenant(), () -> acceptVerified(workload, command, idempotencyKey));
        } catch (DdcConfigSnapshotService.Rejected currentTarget) {
            if (currentTarget.status() == HttpStatus.NOT_FOUND) throw rejected(HttpStatus.CONFLICT, "RECEIPT_TARGET_SUPERSEDED");
            throw currentTarget;
        } catch (DdcConfigDeliveryTokenService.Rejected invalidCredential) {
            throw rejected(HttpStatus.UNAUTHORIZED, "CONFIG_DELIVERY_TOKEN_REJECTED");
        } catch (DdcConfigDeliveryTokenService.Unavailable unavailable) {
            throw rejected(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_DELIVERY_TOKEN_UNAVAILABLE");
        } catch (DdcConfigBootBindingRepository.BindingConflictException wrongBoot) {
            throw rejected(HttpStatus.CONFLICT, "TIANSHU_CONFIG_BOOT_BINDING_CONFLICT");
        } catch (DdcConfigBootBindingRepository.BindingUnavailableException unavailable) {
            throw rejected(HttpStatus.SERVICE_UNAVAILABLE, "TIANSHU_CONFIG_BOOT_BINDING_UNAVAILABLE");
        } catch (DdcConfigProcessReceiptRepository.ReceiptUnavailableException unknown) {
            throw rejected(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_RECEIPT_COMMIT_UNKNOWN");
        } catch (DdcConfigProcessReceiptRepository.ReceiptStoreUnavailableException unavailable) {
            throw rejected(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_RECEIPT_STORE_UNAVAILABLE");
        } catch (IllegalArgumentException invalid) {
            throw rejected(HttpStatus.BAD_REQUEST, "CONFIG_RECEIPT_INVALID");
        }
    }

    private void acceptVerified(DdcConfigWorkloadIdentityVO workload,DdcConfigReceiptCommand command,String idempotencyKey) {
        var claims = tokens.verify(command.getDeliveryToken(), workload);
        if (!Objects.equals(command.getBootId(), workload.bootId())
                || !Objects.equals(claims.bootId(), workload.bootId())) throw rejected(HttpStatus.CONFLICT, "RECEIPT_BOOT_MISMATCH");
        String expectedKey = workload.podUid() + "/" + workload.containerId() + "/" + workload.bootId()
                + "/" + command.getReleaseRevision() + "/" + command.getReportSeq();
        if (!expectedKey.equals(idempotencyKey)) throw rejected(HttpStatus.CONFLICT, "RECEIPT_IDEMPOTENCY_MISMATCH");
        if (!DdcConfigCanonicalJson.receiptDigest(command).equals(command.getReceiptDigest()))
            throw rejected(HttpStatus.BAD_REQUEST, "CONFIG_RECEIPT_DIGEST_INVALID");
        var published = snapshots.requireCurrent(workload);
        var snapshot = published.snapshot();
        if (claims.configId() != published.configId() || !claims.releaseRevision().equals(snapshot.getReleaseRevision())
                || !claims.releaseRevision().equals(command.getReleaseRevision())
                || claims.enabled() != snapshot.getEnabled()
                || !claims.contentSha256().equals(snapshot.getContentSha256())
                || !claims.contentSha256().equals(command.getContentSha256())
                || !claims.restartDigest().equals(snapshot.getRestartDigest())
                || !sameScope(claims.scope(), workload.scope())
                || !sameScope(snapshot.getScope(), workload.scope())
                || !claims.identityTenantId().equals(workload.tenant().identityTenantId())
                || !claims.targetBindingId().equals(workload.targetBindingId())
                || claims.targetBindingRevision() != workload.targetBindingRevision())
            throw rejected(HttpStatus.CONFLICT, "RECEIPT_TARGET_SUPERSEDED");
        if (command.getStatus().isSuccessful() && !claims.restartDigest().equals(command.getBaselineRestartDigest()))
            throw rejected(HttpStatus.CONFLICT, "RECEIPT_RESTART_BASELINE_MISMATCH");
        var receipt = converter.fromCommand(command);
        receipt.setTenantId(workload.tenant().tenantId()).setConfigId(published.configId())
                .setScopeJson(published.manifest().path("scope").deepCopy())
                .setTargetBindingId(workload.targetBindingId()).setTargetRevision(workload.targetBindingRevision())
                .setClusterId(workload.clusterId()).setK8sNamespace(workload.k8sNamespace())
                .setWorkloadUid(workload.workloadUid()).setPodUid(workload.podUid())
                .setContainerName(workload.containerName()).setContainerId(workload.containerId())
                .setBootId(workload.bootId()).setBootFence(claims.bootFence())
                .setAuthorizedManifest(published.manifest().deepCopy());
        // Repository repeats fixed manifest, boot fence and current-pointer checks atomically.
        var accepted = receipts.accept(workload.tenant(), receipt);
        if (accepted == DdcConfigProcessReceiptRepository.AcceptanceEnum.STALE)
            throw rejected(HttpStatus.CONFLICT, "STALE_RECEIPT");
        if (accepted == DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT)
            throw rejected(HttpStatus.CONFLICT, "RECEIPT_CONFLICT");
        if (accepted != DdcConfigProcessReceiptRepository.AcceptanceEnum.SAME
                && accepted != DdcConfigProcessReceiptRepository.AcceptanceEnum.ADVANCED)
            throw rejected(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_RECEIPT_STORE_UNAVAILABLE");
    }

    private boolean sameScope(DdcConfigScopeVO left,DdcConfigScopeVO right) {
        return left != null && right != null && Objects.equals(left.getTenantId(), right.getTenantId())
                && Objects.equals(left.getBizCode(), right.getBizCode()) && Objects.equals(left.getEnv(), right.getEnv())
                && Objects.equals(left.getAppCode(), right.getAppCode()) && Objects.equals(left.getResourceName(), right.getResourceName());
    }

    private Rejected rejected(HttpStatus status,String errorCode) { return new Rejected(status, errorCode); }

    public static final class Rejected extends RuntimeException {
        private final HttpStatus status;
        private final String errorCode;
        public Rejected(HttpStatus status,String errorCode) { super(errorCode); this.status = status; this.errorCode = errorCode; }
        public HttpStatus status() { return status; }
        public String errorCode() { return errorCode; }
    }
}
