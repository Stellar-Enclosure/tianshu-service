package top.egon.cola.component.tianshu.admin.controller.i18n;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.config.DdcI18nWebConfiguration;

@Slf4j
@RestControllerAdvice(assignableTypes = {DdcI18nPageController.class, DdcAdminUiI18nController.class, DdcResourceMetadataController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DdcI18nExceptionAdvice {

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResultRecord<Void>> handleCommon(CommonException exception) {
        return response(httpStatus(exception.getCode()), ResultRecord.failure(exception));
    }

    @ExceptionHandler(DdcI18nWebConfiguration.RequestBodyTooLargeException.class)
    public ResponseEntity<ResultRecord<Void>> handleBodyTooLarge(
            DdcI18nWebConfiguration.RequestBodyTooLargeException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE,
                ResultRecord.failure(56412, "TIANSHU_I18N_REQUEST_TOO_LARGE", "I18n request body is too large"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResultRecord<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ResultRecord.failure(56401, "TIANSHU_I18N_VALIDATION_ERROR", "Unsupported I18n request media type"));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            BindException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ResultRecord<Void>> handleInvalidRequest(Exception exception) {
        log.debug("Invalid Tianshu I18n request type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST,
                ResultRecord.failure(56401, "TIANSHU_I18N_VALIDATION_ERROR", "Invalid I18n request"));
    }

    @ExceptionHandler({org.springframework.dao.TransientDataAccessException.class,
            org.springframework.dao.RecoverableDataAccessException.class})
    public ResponseEntity<ResultRecord<Void>> handlePersistenceUnavailable(Exception exception) {
        log.warn("Tianshu I18n persistence unavailable type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE,
                ResultRecord.failure(56410, "TIANSHU_I18N_PERSISTENCE_UNAVAILABLE", "Page language storage is temporarily unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultRecord<Void>> handleUnexpected(Exception exception) {
        log.error("Tianshu I18n REST request failed type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                ResultRecord.failure(56999, "TIANSHU_INTERNAL_FAILURE", "Tianshu internal failure"));
    }

    private HttpStatus httpStatus(int code) {
        return switch (code) {
            case 56401 -> HttpStatus.BAD_REQUEST;
            case 56402, 56406, 56042, 56432 -> HttpStatus.NOT_FOUND;
            case 56407 -> HttpStatus.FORBIDDEN;
            case 56403, 56404, 56433 -> HttpStatus.CONFLICT;
            case 56412 -> HttpStatus.PAYLOAD_TOO_LARGE;
            case 56410, 56431 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
