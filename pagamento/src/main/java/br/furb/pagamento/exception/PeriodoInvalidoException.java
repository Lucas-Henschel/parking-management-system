package br.furb.pagamento.exception;

/** Saída anterior ou igual à entrada. Erro permanente: estende IllegalArgumentException para não ser retentado. */
public class PeriodoInvalidoException extends IllegalArgumentException {
    public PeriodoInvalidoException() {
        super("A saída deve ser posterior à entrada");
    }
}
