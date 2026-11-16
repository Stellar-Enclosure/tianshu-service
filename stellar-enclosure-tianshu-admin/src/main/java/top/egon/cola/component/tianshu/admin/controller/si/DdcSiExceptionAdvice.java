package top.egon.cola.component.tianshu.admin.controller.si;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.ResultRecord;

/** Scoped exception translator for SI REST endpoints preserving sanitized error codes. */
@Slf4j
@RestControllerAdvice(assignableTypes = DdcSiResourceController.class)
@Order(-2)
public class DdcSiExceptionAdvice {

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResultRecord<Void>> handleCommon(CommonException exception) {
        return response(httpStatus(exception.getCode()),
                ResultRecord.failure(exception.getCode(), exception.getStatus(), exception.getMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            BindException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ResultRecord<Void>> handleValidation(Exception exception) {
        log.debug("Invalid Tianshu SI REST request type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST,
                ResultRecord.failure(56400, "TIANSHU_SI_VALIDATION_ERROR", "Invalid SI governance request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultRecord<Void>> handleUnexpected(Exception exception) {
        log.error("Unexpected SI REST failure type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                ResultRecord.failure(56999, "TIANSHU_SI_INTERNAL_ERROR", "Tianshu SI operation failed"));
    }

    private HttpStatus httpStatus(int code) {
        return switch (code) {
            case 56403 -> HttpStatus.FORBIDDEN;
            case 56404 -> HttpStatus.NOT_FOUND;
            case 56409 -> HttpStatus.CONFLICT;
            case 56422 -> HttpStatus.valueOf(422);
            case 56400 -> HttpStatus.BAD_REQUEST;
            case 56429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
