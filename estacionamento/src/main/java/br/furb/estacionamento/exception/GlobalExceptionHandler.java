package br.furb.estacionamento.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estende ResponseEntityExceptionHandler para que as exceções do Spring MVC (JSON inválido,
 * rota inexistente, método não permitido etc.) mantenham o status correto em formato ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException exception) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", "recurso-nao-encontrado", exception.getMessage());
    }

    @ExceptionHandler(ConflitoNegocioException.class)
    public ProblemDetail tratarConflito(ConflitoNegocioException exception) {
        return problema(HttpStatus.CONFLICT, "Conflito de negócio", "conflito-negocio", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail tratarIntegridade(DataIntegrityViolationException exception) {
        return problema(
            HttpStatus.CONFLICT,
            "Conflito de dados",
            "conflito-dados",
            "A entrada conflita com um veículo ou ticket já cadastrado."
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException exception) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "argumento-invalido", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tratarTipoInvalido(MethodArgumentTypeMismatchException exception) {
        String tipo = exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : "desconhecido";

        return problema(
            HttpStatus.BAD_REQUEST,
            "Tipo de argumento inválido",
            "tipo-invalido",
            String.format("O parâmetro '%s' deve ser do tipo %s", exception.getName(), tipo)
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        ProblemDetail problema = problema(
            HttpStatus.BAD_REQUEST,
            "Erro de validação",
            "validacao",
            "Erro de validação nos dados fornecidos"
        );

        Map<String, String> erros = new LinkedHashMap<>();

        exception.getBindingResult()
            .getFieldErrors()
            .forEach((FieldError erro) -> erros.put(erro.getField(), erro.getDefaultMessage()));

        problema.setProperty("errors", erros);

        return ResponseEntity.badRequest().body(problema);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarErroGenerico(Exception exception) {
        log.error("Erro não tratado", exception);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "erro-interno", "Erro interno do servidor");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String tipo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);

        problema.setTitle(titulo);
        problema.setType(URI.create("https://api.parking.com/errors/" + tipo));

        return problema;
    }
}
