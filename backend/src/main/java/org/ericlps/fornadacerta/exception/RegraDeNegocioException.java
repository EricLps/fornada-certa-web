package org.ericlps.fornadacerta.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String message){
        super(message);
    }

}
