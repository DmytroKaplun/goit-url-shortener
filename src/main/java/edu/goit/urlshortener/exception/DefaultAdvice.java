package edu.goit.urlshortener.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@ControllerAdvice
public class DefaultAdvice {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleException(BusinessException e) {
        ApiError apiError = new ApiError(e.getMessage());
        log.info(e.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.OK);
    }

    @ExceptionHandler({MyEntityNotFoundException.class, EntityNotFoundException.class, AuthenticationException.class})
    protected ResponseEntity<Object> handleEntityNotFoundEx(MyEntityNotFoundException ex, WebRequest request) {
        ApiError apiError = new ApiError("Entity Not Found Exception", ex.getMessage());
        log.info(ex.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleException(CustomException e) {
        ApiError apiError = new ApiError(e.getMessage());
        log.info(e.getMessage());
        return new ResponseEntity<>(apiError, HttpStatus.OK);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                      WebRequest request) {
        ApiError apiError = new ApiError();
        apiError.setMessage(String.format("The parameter '%s' of value '%s' could not be converted to type '%s'",
                ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName()));
        apiError.setDebugMessage(ex.getMessage());
        log.info(ex.getMessage());
        return new ResponseEntity<>(apiError, BAD_REQUEST);
    }
}
