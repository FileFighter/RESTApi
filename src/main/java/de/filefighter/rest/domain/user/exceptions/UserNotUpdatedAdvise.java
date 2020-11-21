package de.filefighter.rest.domain.user.exceptions;

import de.filefighter.rest.rest.ServerResponse;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
class UserNotUpdatedAdvise {

    @ResponseBody
    @ExceptionHandler(UserNotUpdatedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ResponseEntity<ServerResponse> userNotUpdatedExceptionHandler(UserNotUpdatedException ex) {
        LoggerFactory.getLogger(UserNotUpdatedException.class).warn(ex.getMessage());
        return new ResponseEntity<>(new ServerResponse(HttpStatus.CONFLICT, ex.getMessage()), HttpStatus.CONFLICT);
    }
}