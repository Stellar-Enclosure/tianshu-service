package top.egon.cola.component.tianshu.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirementEntry;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliverySecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigReceiptService;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigSnapshotService;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;

import java.util.List;
import java.util.Objects;

/** Internal dual-credential protocol; workload verification occurs before MVC body parsing. */
@Slf4j
@RestController("ddcConfigDeliveryController")
@RequestMapping("/internal/v1/config")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnProperty(prefix = "egon.cola.component.tianshu.admin.config-delivery", name = "enabled", havingValue = "true")
@Tag(name = "tianshu-internal-config-delivery")
@SecurityScheme(name = "tianshuConfigServiceBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "tianshuConfigWorkloadToken", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-Egon-Workload-Token")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = DdcConfigDeliveryController.DeliveryErrorVO.class))),
        @ApiResponse(responseCode = "401", description = "Credential invalid or expired", content = @Content(schema = @Schema(implementation = DdcConfigDeliveryController.DeliveryErrorVO.class))),
        @ApiResponse(responseCode = "403", description = "Scope or target authorization denied", content = @Content(schema = @Schema(implementation = DdcConfigDeliveryController.DeliveryErrorVO.class))),
        @ApiResponse(responseCode = "409", description = "Boot, target or receipt sequence conflict", content = @Content(schema = @Schema(implementation = DdcConfigDeliveryController.DeliveryErrorVO.class))),
        @ApiResponse(responseCode = "503", description = "Authority, observation or persistence unavailable", content = @Content(schema = @Schema(implementation = DdcConfigDeliveryController.DeliveryErrorVO.class)))})
public class DdcConfigDeliveryController {
    public static final String VERIFIED_WORKLOAD_ATTRIBUTE = "top.egon.cola.component.tianshu.admin.controller.config.DdcConfigDeliveryController.verifiedWorkload";
    private static final String DELIVERY_TOKEN_HEADER = "X-Config-Delivery-Token";
    private static final String VALID_UNTIL_HEADER = "X-Config-Valid-Until";
    private static final String CONTAINER_ID_HEADER = "X-Config-Container-Id";
    @Qualifier("ddcConfigSnapshotService") private final DdcConfigSnapshotService snapshots;
    @Qualifier("ddcConfigReceiptService") private final DdcConfigReceiptService receipts;

    @GetMapping("/snapshot")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @Operation(operationId = "configSnapshot", summary = "Read one authorized complete HOT configuration snapshot",
            parameters = {@Parameter(name = "X-Egon-Workload-Token", in = ParameterIn.HEADER, required = true),
                    @Parameter(name = "X-Config-Boot-Id", in = ParameterIn.HEADER, required = true)},
            security = @SecurityRequirement(name = "tianshuConfigServiceBearer",
                    combine = @SecurityRequirementEntry(name = "tianshuConfigWorkloadToken")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Complete snapshot and fresh delivery credential", content = @Content(schema = @Schema(implementation = DdcConfigSnapshotDTO.class)),
                    headers = {@Header(name = "ETag"), @Header(name = DELIVERY_TOKEN_HEADER), @Header(name = VALID_UNTIL_HEADER),
                            @Header(name = CONTAINER_ID_HEADER, description = "Verified opaque container incarnation including its runtime prefix", schema = @Schema(type = "string"))}),
            @ApiResponse(responseCode = "304", description = "Reauthorized existing boot and unchanged content", content = @Content,
                    headers = {@Header(name = "ETag"), @Header(name = DELIVERY_TOKEN_HEADER), @Header(name = VALID_UNTIL_HEADER),
                            @Header(name = CONTAINER_ID_HEADER, description = "Verified opaque container incarnation including its runtime prefix", schema = @Schema(type = "string"))}),
            @ApiResponse(responseCode = "404", description = "No authorized published configuration", content = @Content(schema = @Schema(implementation = DeliveryErrorVO.class)))})
    public ResponseEntity<DdcConfigSnapshotDTO> configSnapshot(
            @Parameter(hidden = true) @AuthenticationPrincipal ServiceIdentityPrincipal principal,
            @Parameter(hidden = true) @RequestAttribute(name = VERIFIED_WORKLOAD_ATTRIBUTE, required = false) DdcConfigWorkloadIdentityVO workload,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch, HttpServletRequest request) {
        requireVerified(principal, workload);
        var snapshot = snapshots.snapshot(workload, ifNoneMatch);
        var headers = headers(request);
        headers.setCacheControl("private, no-cache");
        headers.setETag(snapshot.etag());
        headers.set(DELIVERY_TOKEN_HEADER, snapshot.deliveryToken());
        headers.set(VALID_UNTIL_HEADER, snapshot.content().getValidUntil().toString());
        headers.set(CONTAINER_ID_HEADER, workload.containerId());
        return new ResponseEntity<>(snapshot.notModified() ? null : snapshot.content(), headers,
                snapshot.notModified() ? HttpStatus.NOT_MODIFIED : HttpStatus.OK);
    }

    @PostMapping(value = "/receipts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @Operation(operationId = "reportConfigReceipt", summary = "Persist a verified process result without changing the desired revision",
            parameters = {@Parameter(name = "X-Egon-Workload-Token", in = ParameterIn.HEADER, required = true),
                    @Parameter(name = "X-Config-Boot-Id", in = ParameterIn.HEADER, required = true)},
            security = @SecurityRequirement(name = "tianshuConfigServiceBearer",
                    combine = @SecurityRequirementEntry(name = "tianshuConfigWorkloadToken")))
    @ApiResponse(responseCode = "204", description = "Durably accepted or an identical report already exists", content = @Content)
    public ResponseEntity<Void> reportConfigReceipt(
            @Parameter(hidden = true) @AuthenticationPrincipal ServiceIdentityPrincipal principal,
            @Parameter(hidden = true) @RequestAttribute(name = VERIFIED_WORKLOAD_ATTRIBUTE, required = false) DdcConfigWorkloadIdentityVO workload,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DdcConfigReceiptCommand command, HttpServletRequest request) {
        requireVerified(principal, workload);
        receipts.accept(workload, command, idempotencyKey);
        return new ResponseEntity<>(null, headers(request), HttpStatus.NO_CONTENT);
    }

    @ExceptionHandler(DdcConfigReceiptService.Rejected.class)
    public ResponseEntity<DeliveryErrorVO> receiptRejected(DdcConfigReceiptService.Rejected failure, HttpServletRequest request) {
        return error(request, failure.status(), failure.errorCode());
    }

    @ExceptionHandler(DdcConfigSnapshotService.Rejected.class)
    public ResponseEntity<DeliveryErrorVO> snapshotRejected(DdcConfigSnapshotService.Rejected failure, HttpServletRequest request) {
        return error(request, failure.status(), failure.errorCode());
    }

    @ExceptionHandler(DdcConfigWorkloadVerifier.Rejected.class)
    public ResponseEntity<DeliveryErrorVO> workloadRejected(DdcConfigWorkloadVerifier.Rejected failure, HttpServletRequest request) {
        return error(request, failure.status(), failure.errorCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, ServletRequestBindingException.class, IllegalArgumentException.class})
    public ResponseEntity<DeliveryErrorVO> invalidRequest(Exception ignored, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "CONFIG_REQUEST_INVALID");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<DeliveryErrorVO> unavailable(RuntimeException failure, HttpServletRequest request) {
        log.warn("Configuration delivery unavailable; failureType={}", failure.getClass().getSimpleName());
        return error(request, HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_DELIVERY_UNAVAILABLE");
    }

    private void requireVerified(ServiceIdentityPrincipal principal,DdcConfigWorkloadIdentityVO workload) {
        if (principal == null || workload == null)
            throw new DdcConfigReceiptService.Rejected(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID");
        if (principal.scopeContext() != ServiceTokenContext.TENANT
                || !Objects.equals(principal.tenantId(), workload.tenant().identityTenantId()))
            throw new DdcConfigReceiptService.Rejected(HttpStatus.FORBIDDEN, "AUTHORIZATION_DENIED");
    }

    private HttpHeaders headers(HttpServletRequest request) {
        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setVary(List.of(HttpHeaders.AUTHORIZATION, "X-Egon-Workload-Token", "X-Config-Boot-Id"));
        headers.set("x-egon-request-id", DdcConfigDeliverySecurityConfiguration.requestId(request));
        return headers;
    }

    private ResponseEntity<DeliveryErrorVO> error(HttpServletRequest request,HttpStatus status,String errorCode) {
        boolean audited = switch (status) {
            case UNAUTHORIZED -> IdpSecurityObservationFilter.beforeAuthenticationFailure(request);
            case FORBIDDEN -> IdpSecurityObservationFilter.beforeAuthorizationDenied(request);
            default -> true;
        };
        HttpStatus effectiveStatus = audited ? status : HttpStatus.SERVICE_UNAVAILABLE;
        String effectiveCode = audited ? errorCode : "OAUTH_AUDIT_DELIVERY_UNAVAILABLE";
        var headers = headers(request);
        if (effectiveStatus == HttpStatus.UNAUTHORIZED) headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return new ResponseEntity<>(new DeliveryErrorVO(effectiveCode, headers.getFirst("x-egon-request-id")), headers, effectiveStatus);
    }

    public record DeliveryErrorVO(String errorCode,String requestId) { }
}
