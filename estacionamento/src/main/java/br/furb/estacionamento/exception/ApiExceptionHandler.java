package br.furb.estacionamento.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail tratarConflitoDeIntegridade(DataIntegrityViolationException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A entrada conflita com um veículo ou ticket já cadastrado"
        );
        problemDetail.setTitle("Conflito ao registrar entrada");
        return problemDetail;
    }
}