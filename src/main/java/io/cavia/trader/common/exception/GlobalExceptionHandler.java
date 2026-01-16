package io.cavia.trader.common.exception;

import io.cavia.trader.common.response.ApiResponse;
import io.cavia.trader.common.response.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러 전역에서 발생하는 모든 예외를 처리해 ResponseBody 형식으로 응답하는 클래스
 *
 * @author KimBeomhee
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 우리가 직접 정의한 비즈니스 예외(ApiException)를 처리합니다.
     *
     * @param e ApiException
     * @return ApiResponse가 담긴 ResponseEntity
     */
    @ExceptionHandler(ApiException.class)
    protected ResponseEntity<ApiResponse<?>> handleCustomException(ApiException e) {

        ErrorCode errorCode = e.getErrorCode();
        log.debug("Business Exception Occurred: {}", errorCode.getMessage());

        return ApiResponses.of(errorCode.getHttpStatus(), errorCode.getMessage());
    }

    /**
     * {@code @Valid} 어노테이션 유효성 검사 실패 시, 첫 번째 에러 메시지만을 응답합니다.
     *
     * @param e MethodArgumentNotValidException
     * @return ApiResponse가 담긴 ResponseEntity
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationExceptions(MethodArgumentNotValidException e) {

        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        return ApiResponses.of(errorCode.getHttpStatus(), errorMessage);
    }

    /**
     * 서블릿에서 없는 리소스를 요청하면 발생하는 예외를 처리합니다.
     * ex)No static resource images/logo.png
     *
     * @param e NoResourceFoundException
     * @return ApiResponse가 담긴 ResponseEntity
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationExceptions(NoResourceFoundException e) {

        return ApiResponses.of(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Repository 계층에서 발생한 예외를 처리합니다.
     *
     * @param e DataAccessException
     * @return ApiResponse가 담긴 ResponseEntity
     */
    @ExceptionHandler(DataAccessException.class)
    protected ResponseEntity<ApiResponse<?>> handleException(DataAccessException e) {

        ErrorCode errorCode = ErrorCode.DATABASE_OPERATION_FAILED;
        log.error("Data Access Exception Occurred", e);

        return ApiResponses.of(errorCode.getHttpStatus(), errorCode.getMessage());
    }

    /**
     * 이외의 처리하지 못한 모든 예외를 처리합니다.
     *
     * @param e Exception
     * @return ApiResponse가 담긴 ResponseEntity
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<?>> handleException(Exception e) {

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("Unhandled Exception Occurred", e);

        return ApiResponses.of(httpStatus, httpStatus.getReasonPhrase());
    }
}