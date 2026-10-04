package br.furb.pagamento.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.util.ErrorHandler;

public class RabbitMQErrorHandler implements ErrorHandler {

    @Override
    public void handleError(Throwable t) {
        // Extrair a causa raiz da exceção
        Throwable rootCause = getRootCause(t);
        
        // Verificar se é um erro permanente que não deve ser retentado
        if (isNonRetryableException(rootCause)) {
            // Rejeitar e não recolocar na fila - vai direto para DLQ
            throw new AmqpRejectAndDontRequeueException(
                    "Erro permanente detectado - enviando para DLQ: " + rootCause.getMessage(),
                    rootCause
            );
        }
        
        // Para outros erros, deixar o comportamento padrão de retry
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new RuntimeException(t);
    }
    
    private boolean isNonRetryableException(Throwable throwable) {
        // IllegalArgumentException indica erro de validação/negócio que não será corrigido com retry
        if (throwable instanceof IllegalArgumentException) {
            return true;
        }
        
        // MessageConversionException indica problema no formato da mensagem
        if (throwable instanceof MessageConversionException) {
            return true;
        }
        
        // Se for ListenerExecutionFailedException, verificar a causa
        if (throwable instanceof ListenerExecutionFailedException) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                return isNonRetryableException(cause);
            }
        }
        
        return false;
    }
    
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
