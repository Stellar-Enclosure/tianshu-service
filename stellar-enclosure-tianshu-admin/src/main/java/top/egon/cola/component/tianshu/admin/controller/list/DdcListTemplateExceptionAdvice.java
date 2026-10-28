package top.egon.cola.component.tianshu.admin.controller.list;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.ResultRecord;

@RestControllerAdvice(assignableTypes = {DdcListTemplateController.class, DdcNamespaceExportController.class, DdcListDelegationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DdcListTemplateExceptionAdvice {
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResultRecord<Void>> common(CommonException failure) {
        var status = switch (failure.getCode()) {
            case 56401 -> HttpStatus.BAD_REQUEST;
            case 56402 -> HttpStatus.NOT_FOUND;
            case 56404 -> HttpStatus.CONFLICT;
            case 56412 -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(ResultRecord.failure(failure));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultRecord<Void>> forbidden(AccessDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56407, "TIANSHU_LIST_TEMPLATE_FORBIDDEN", "List template access denied"));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ResultRecord<Void>> invalid(Exception failure) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56401, "TIANSHU_LIST_TEMPLATE_INVALID", "Invalid list template request"));
    }

    @ExceptionHandler(top.egon.cola.component.tianshu.admin.config.DdcListDelegationWebConfiguration.BodyTooLargeException.class)
    public ResponseEntity<ResultRecord<Void>> tooLarge(Exception failure) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56412, "TIANSHU_LIST_DELEGATION_TOO_LARGE", "List request is too large"));
    }

    @ExceptionHandler(top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationDeniedException.class)
    public ResponseEntity<ResultRecord<Void>> authorityDenied(Exception failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56407, "TIANSHU_LIST_TEMPLATE_FORBIDDEN", "List template access denied"));
    }

    @ExceptionHandler(top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationUnavailableException.class)
    public ResponseEntity<ResultRecord<Void>> authorityUnavailable(Exception failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56410, "TIANSHU_LIST_AUTHORIZATION_UNAVAILABLE", "List authorization is unavailable"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ResultRecord<Void>> constraint(org.springframework.dao.DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.postgresql.util.PSQLException postgres && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null && postgres.getServerErrorMessage().getConstraint() != null
                    && java.util.Set.of("uk_list_template_name", "uk_list_template_default")
                    .contains(postgres.getServerErrorMessage().getConstraint())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).header("Cache-Control", "no-store")
                        .body(ResultRecord.failure(56404, "TIANSHU_LIST_TEMPLATE_CONFLICT", "List template changed or its name already exists"));
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56999, "TIANSHU_INTERNAL_FAILURE", "Tianshu internal failure"));
    }

    @ExceptionHandler({IllegalStateException.class, org.springframework.dao.TransientDataAccessException.class,
            org.springframework.dao.RecoverableDataAccessException.class})
    public ResponseEntity<ResultRecord<Void>> unavailable(Exception failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store")
                .body(ResultRecord.failure(56410, "TIANSHU_LIST_METADATA_UNAVAILABLE", "List metadata is temporarily unavailable"));
    }
}
