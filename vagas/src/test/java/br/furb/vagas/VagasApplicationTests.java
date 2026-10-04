package br.furb.vagas;

import br.furb.vagas.entity.*;
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
    @Test
    void deveImpedirReservaDeVagaBloqueada() {
        Vaga vaga = new Vaga("A-12", UUID.randomUUID(), UUID.randomUUID());
        vaga.alterarBloqueio(true);
        assertThatThrownBy(() -> vaga.reservar(UUID.randomUUID())).isInstanceOf(ConflitoNegocioException.class);
        vaga.alterarBloqueio(false);
        assertThat(vaga.obterStatus()).isEqualTo(VagaStatus.LIVRE);
    }
    @Test
    void deveProtegerAlteracaoDeVagaOcupada() {
        Vaga vaga = new Vaga("A-12", UUID.randomUUID(), UUID.randomUUID());
        vaga.reservar(UUID.randomUUID());
        assertThatThrownBy(() -> vaga.alterarBloqueio(true)).isInstanceOf(ConflitoNegocioException.class);
        assertThatThrownBy(() -> vaga.atualizar("B-1", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ConflitoNegocioException.class);
    }
}
