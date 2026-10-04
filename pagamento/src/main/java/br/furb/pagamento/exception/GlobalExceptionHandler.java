package br.furb.pagamento.exception;

import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PagamentoNaoEncontradoException.class)
    public ProblemDetail tratarPagamentoNaoEncontrado(PagamentoNaoEncontradoException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problemDetail.setTitle("Pagamento não encontrado");
        problemDetail.setType(URI.create("https://api.parking.com/errors/pagamento-nao-encontrado"));
        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail tratarEstadoInvalido(IllegalStateException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problemDetail.setTitle("Estado inválido");
        problemDetail.setType(URI.create("https://api.parking.com/errors/estado-invalido"));
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
        problemDetail.setTitle("Argumento inválido");
        problemDetail.setType(URI.create("https://api.parking.com/errors/argumento-invalido"));
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Erro de validação nos dados fornecidos"
        );
        problemDetail.setTitle("Erro de validação");
        problemDetail.setType(URI.create("https://api.parking.com/errors/validacao"));
        
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        problemDetail.setProperty("errors", errors);
        
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tratarTipoInvalido(MethodArgumentTypeMismatchException exception) {
        String message = String.format("O parâmetro '%s' deve ser do tipo %s", 
                exception.getName(), 
                exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : "desconhecido");
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                message
        );
        problemDetail.setTitle("Tipo de argumento inválido");
        problemDetail.setType(URI.create("https://api.parking.com/errors/tipo-invalido"));
        return problemDetail;
    }

    @ExceptionHandler(MessageConversionException.class)
    public ProblemDetail tratarConversaoMensagem(MessageConversionException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Erro ao converter mensagem: " + exception.getMessage()
        );
        problemDetail.setTitle("Erro de conversão de mensagem");
        problemDetail.setType(URI.create("https://api.parking.com/errors/conversao-mensagem"));
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarErroGenerico(Exception exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor"
        );
        problemDetail.setTitle("Erro interno");
        problemDetail.setType(URI.create("https://api.parking.com/errors/erro-interno"));
        return problemDetail;
    }
}
