package top.egon.cola.component.tianshu.admin.controller.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminAuthenticationEntryPoint;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nSourceImportJson;

import java.io.IOException;

@Slf4j
@RestControllerAdvice(assignableTypes = DdcI18nSourceImportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DdcI18nSourceImportExceptionAdvice {
    private final org.springframework.beans.factory.ObjectProvider<DdcAdminAuthenticationEntryPoint> securityErrors;

    public DdcI18nSourceImportExceptionAdvice(
            @Qualifier("ddcAdminAuthenticationEntryPoint")
            org.springframework.beans.factory.ObjectProvider<DdcAdminAuthenticationEntryPoint> securityErrors) {
        this.securityErrors = securityErrors;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void denied(AccessDeniedException denied, HttpServletRequest request, HttpServletResponse response) throws IOException {
        securityErrors.getObject().handle(request, response, denied);
    }

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResultRecord<Void>> conflictOrMissing(CommonException failure) {
        HttpStatus status = switch (failure.getCode()) {
            case 56421 -> HttpStatus.CONFLICT;
            case 56422 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(ResultRecord.failure(failure));
    }

    @ExceptionHandler(DdcI18nSourceImportJson.BodyTooLargeException.class)
    public ResponseEntity<ResultRecord<Void>> tooLarge() {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, 56424, "TIANSHU_METADATA_IMPORT_TOO_LARGE");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ResultRecord<Void>> invalid() {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, 56425, "TIANSHU_METADATA_IMPORT_INVALID");
    }

    @ExceptionHandler({org.springframework.dao.TransientDataAccessException.class, org.springframework.dao.RecoverableDataAccessException.class,
            org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ResultRecord<Void>> unavailable(Exception failure) {
        log.warn("Tianshu metadata persistence unavailable type={}", failure.getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE, 56426, "TIANSHU_METADATA_PERSISTENCE_UNAVAILABLE");
    }

    private ResponseEntity<ResultRecord<Void>> response(HttpStatus status, int code, String reason) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(ResultRecord.failure(code, reason, reason));
    }
}
