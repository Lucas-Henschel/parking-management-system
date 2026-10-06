package br.furb.estacionamento.entity;

import br.furb.estacionamento.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketTest {
    private final Instant entrada = Instant.parse("2026-10-05T12:00:00Z");

    private Ticket novo() {
        return new Ticket(new Veiculo("ABC1234"), entrada);
    }

    @Test
    void nasceComoPendenteComUmaTentativaDeReserva() {
        Ticket ticket = novo();

        assertEquals(TicketStatus.PENDENTE, ticket.getStatus());
        assertEquals(1, ticket.getTentativasReserva());
        assertEquals(entrada, ticket.getEntrada());
        assertNull(ticket.getVagaId());
    }

    @Test
    void confirmarVagaAtivaOTicket() {
        Ticket ticket = novo();
        UUID vagaId = UUID.randomUUID();

        ticket.confirmarVaga(vagaId);

        assertEquals(TicketStatus.ATIVO, ticket.getStatus());
        assertEquals(vagaId, ticket.getVagaId());
    }

    @Test
    void recusarEncerraOTicketComASaidaInformada() {
        Ticket ticket = novo();
        Instant saida = entrada.plusSeconds(60);

        ticket.recusar(saida);

        assertEquals(TicketStatus.RECUSADO, ticket.getStatus());
        assertEquals(saida, ticket.getSaida());
    }

    @Test
    void novaTentativaIncrementaOContador() {
        Ticket ticket = novo();

        ticket.registrarNovaTentativaReserva();

        assertEquals(2, ticket.getTentativasReserva());
    }

    @Test
    void registrarCalculoColocaOTicketAguardandoPagamento() {
        Ticket ticket = novo();
        UUID pagamentoId = UUID.randomUUID();

        ticket.registrarCalculo(pagamentoId, new BigDecimal("20.00"));

        assertEquals(TicketStatus.AGUARDANDO_PAGAMENTO, ticket.getStatus());
        assertEquals(pagamentoId, ticket.getPagamentoId());
        assertEquals(new BigDecimal("20.00"), ticket.getValor());
    }

    @Test
    void confirmacaoAntecipadaGuardaOValorSemMudarOStatus() {
        Ticket ticket = novo();
        ticket.confirmarVaga(UUID.randomUUID());
        UUID pagamentoId = UUID.randomUUID();

        ticket.registrarConfirmacaoAntecipada(pagamentoId, new BigDecimal("20.00"));

        assertEquals(TicketStatus.ATIVO, ticket.getStatus());
        assertEquals(pagamentoId, ticket.getPagamentoId());
        assertEquals(new BigDecimal("20.00"), ticket.getValorConfirmado());
    }

    @Test
    void finalizarGuardaOValorConfirmado() {
        Ticket ticket = novo();

        ticket.finalizar(new BigDecimal("20.00"));

        assertEquals(TicketStatus.FINALIZADO, ticket.getStatus());
        assertEquals(new BigDecimal("20.00"), ticket.getValorConfirmado());
    }
}
