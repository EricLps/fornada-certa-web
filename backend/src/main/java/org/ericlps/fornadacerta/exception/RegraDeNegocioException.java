package org.ericlps.fornadacerta.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

public class RegraDeNegocioException extends RuntimeException {

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail tratarRegraDeNegocio(RegraDeNegocioException ex) {

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
        problema.setTitle("Regra de negócio violada");
        return problema;
    }
}
