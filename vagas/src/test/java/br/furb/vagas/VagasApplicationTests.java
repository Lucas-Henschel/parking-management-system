package br.furb.vagas;

import br.furb.vagas.entity.*;
import br.furb.vagas.enums.*;
import br.furb.vagas.exception.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VagasApplicationTests {
    @Test
    void deveReservarELiberarVagaDoMesmoTicket() {
        Vaga vaga = new Vaga("A-12", UUID.randomUUID(), UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        vaga.reservar(ticketId);
        assertThat(vaga.obterStatus()).isEqualTo(VagaStatus.OCUPADA);
        assertThat(vaga.obterTicketId()).isEqualTo(ticketId);
        vaga.liberar(ticketId);
        assertThat(vaga.obterStatus()).isEqualTo(VagaStatus.LIVRE);
        assertThat(vaga.obterTicketId()).isNull();
    }
    @Test
    void deveImpedirLiberacaoPorOutroTicket() {
        Vaga vaga = new Vaga("A-12", UUID.randomUUID(), UUID.randomUUID());
        UUID ticketId = UUID.randomUUID(); vaga.reservar(ticketId);
        assertThatThrownBy(() -> vaga.liberar(UUID.randomUUID())).isInstanceOf(ConflitoNegocioException.class);
        assertThat(vaga.obterTicketId()).isEqualTo(ticketId);
    }
}
