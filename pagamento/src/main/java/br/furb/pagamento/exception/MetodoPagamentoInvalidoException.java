package br.furb.pagamento.exception;

/** Método de pagamento inexistente. Erro permanente: estende IllegalArgumentException para não ser retentado. */
public class MetodoPagamentoInvalidoException extends IllegalArgumentException {
    private final String metodo;

    public MetodoPagamentoInvalidoException(String metodo) {
        super(String.format("Método de pagamento '%s' não encontrado", metodo));
        this.metodo = metodo;
    }

    public String getMetodo() {
        return metodo;
    }
}
