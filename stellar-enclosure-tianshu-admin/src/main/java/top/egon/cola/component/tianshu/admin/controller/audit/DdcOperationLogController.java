package top.egon.cola.component.tianshu.admin.controller.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminAuthenticationEntryPoint;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import top.egon.cola.component.common.core.enums.ResultCode;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.controller.ai.DdcAdminSecurityErrorResult;
import top.egon.cola.component.tianshu.admin.model.query.DdcOperationLogPageQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogDetailVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogPageResult;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.admin.service.audit.DdcOperationLogQueryService;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Set;

/** Read-only audit browsing. Authentication supplies the tenant; public query fields can only narrow it. */
@Slf4j
@RestController("ddcOperationLogController")
@RequestMapping(path = "/api/v1/tianshu/operation-logs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tianshu Operation Logs", description = "Bounded tenant operation-log queries")
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin", entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@SecurityRequirement(name = "tianshuBearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "56000 / TIANSHU_INVALID_REQUEST",
                headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                content = @Content(schema = @Schema(implementation = ResultRecord.class))),
        @ApiResponse(responseCode = "401", description = "TIANSHU_ADMIN_AUTHENTICATION_REQUIRED",
                headers = {@Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                        @Header(name = "WWW-Authenticate", schema = @Schema(type = "string", example = "Bearer"))},
                content = @Content(schema = @Schema(implementation = DdcAdminSecurityErrorResult.class))),
        @ApiResponse(responseCode = "403", description = "TIANSHU_ADMIN_CAPABILITY_REQUIRED or TIANSHU_ADMIN_TENANT_MISMATCH",
                headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                content = @Content(schema = @Schema(implementation = DdcAdminSecurityErrorResult.class))),
        @ApiResponse(responseCode = "500", description = "56999 / TIANSHU_INTERNAL_FAILURE",
                headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                content = @Content(schema = @Schema(implementation = ResultRecord.class))),
        @ApiResponse(responseCode = "503", description = "56998 / TIANSHU_OPERATION_LOG_UNAVAILABLE; security audit may return OAUTH_AUDIT_DELIVERY_UNAVAILABLE",
                headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                content = @Content(schema = @Schema(oneOf = {ResultRecord.class, DdcAdminSecurityErrorResult.class})))
})
public class DdcOperationLogController {
    private static final Set<String> PAGE_PARAMETERS = Set.of("from", "to", "limit", "beforeTime", "beforeId",
            "actionCode", "actorId", "resourceType", "resourceId", "bizCode", "namespaceCode", "env", "appCode", "outcome");
    @Qualifier("ddcOperationLogQueryService") private final DdcOperationLogQueryService queries;
    @Qualifier("ddcAdminAuthenticationEntryPoint") private final DdcAdminAuthenticationEntryPoint securityHandler;

    @Parameters(@Parameter(name = "X-Egon-Tianshu-Tenant", in = ParameterIn.HEADER, required = true,
            description = "Expected identity tenant only; authenticated identity determines the tenant",
            schema = @Schema(type = "string")))
    @Operation(operationId = "tianshu.ddcOperationLogController.page", summary = "Page tenant operation-log summaries")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.operationLogs.page", type = ResourceType.API,
            labelPage = "tianshu.operationLogs", labelKey = "api.page")
    @RequiresPermissions(codes = DdcPermissionCodes.OPERATION_LOG_READ)
    @ApiResponse(responseCode = "200", description = "Bounded summaries and exact half-open time anchors; no total/count",
            headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")))
    @GetMapping("/page")
    public ResponseEntity<ResultRecord<DdcOperationLogPageResult>> page(
            @Valid @ModelAttribute @ParameterObject DdcOperationLogPageQuery query, HttpServletRequest request) {
        requireDeclaredParameters(request, PAGE_PARAMETERS);
        return response(HttpStatus.OK, ResultRecord.success(queries.page(query)));
    }

    @Parameters(@Parameter(name = "X-Egon-Tianshu-Tenant", in = ParameterIn.HEADER, required = true,
            description = "Expected identity tenant only; authenticated identity determines the tenant",
            schema = @Schema(type = "string")))
    @Operation(operationId = "tianshu.ddcOperationLogController.detail", summary = "Read one exact tenant operation-log event")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.operationLogs.detail", type = ResourceType.API,
            labelPage = "tianshu.operationLogs", labelKey = "api.detail")
    @RequiresPermissions(codes = {DdcPermissionCodes.OPERATION_LOG_READ, DdcPermissionCodes.OPERATION_LOG_DETAIL})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Single authorized event, located by original microsecond time and ID",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store"))),
            @ApiResponse(responseCode = "404", description = "404000 / NOT_FOUND",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                    content = @Content(schema = @Schema(implementation = ResultRecord.class)))
    })
    @GetMapping("/{eventId}")
    public ResponseEntity<ResultRecord<DdcOperationLogDetailVO>> detail(
            @PathVariable("eventId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
            @Parameter(schema = @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$"), description = "Positive signed bigint decimal ID; no leading plus") String eventId,
            @RequestParam("occurredAt") @NotBlank @Size(max = 64) @Pattern(regexp = "[^\\x00-\\x1F\\x7F]+")
            @Parameter(schema = @Schema(type = "string", format = "date-time"), description = "Original RFC3339 offset time; at most six fractional digits") String occurredAt,
            HttpServletRequest request) {
        requireDeclaredParameters(request, Set.of("occurredAt"));
        return response(HttpStatus.OK, ResultRecord.success(queries.detail(eventId, occurredAt)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void denied(AccessDeniedException failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        securityHandler.handle(request, response, failure);
    }

    @ExceptionHandler(AuthenticationException.class)
    public void unauthenticated(AuthenticationException failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        securityHandler.commence(request, response, failure);
    }

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResultRecord<Void>> handleCommon(CommonException failure) {
        if (failure.getCode() == DdcErrorStatus.INVALID_REQUEST.getCode()) {
            return response(HttpStatus.BAD_REQUEST, ResultRecord.failure(DdcErrorStatus.INVALID_REQUEST));
        }
        if (failure.getCode() == ResultCode.NOT_FOUND.getCode()) {
            return response(HttpStatus.NOT_FOUND, ResultRecord.failure(ResultCode.NOT_FOUND));
        }
        if (failure.getCode() == DdcErrorStatus.OPERATION_LOG_UNAVAILABLE.getCode()) {
            return response(HttpStatus.SERVICE_UNAVAILABLE, ResultRecord.failure(DdcErrorStatus.OPERATION_LOG_UNAVAILABLE));
        }
        return unexpected(failure);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, TypeMismatchException.class,
            BindException.class, HandlerMethodValidationException.class, ConstraintViolationException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ResultRecord<Void>> invalid(Exception failure) {
        return response(HttpStatus.BAD_REQUEST, ResultRecord.failure(DdcErrorStatus.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultRecord<Void>> unexpected(Exception failure) {
        log.error("Operation log REST query failed type={}", failure.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ResultRecord.failure(DdcErrorStatus.INTERNAL_FAILURE));
    }

    private void requireDeclaredParameters(HttpServletRequest request, Set<String> declared) {
        if (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null
                || request.getParameterMap().entrySet().stream().anyMatch(entry -> !declared.contains(entry.getKey())
                        || entry.getValue() == null || entry.getValue().length != 1)) {
            throw new CommonException(DdcErrorStatus.INVALID_REQUEST);
        }
    }

    private <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
