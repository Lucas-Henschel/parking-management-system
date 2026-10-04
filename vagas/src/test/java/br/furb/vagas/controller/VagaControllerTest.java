package br.furb.vagas.controller;

import br.furb.vagas.exception.ConflitoNegocioException;
import br.furb.vagas.exception.GlobalExceptionHandler;
import br.furb.vagas.service.CadastroService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VagaControllerTest {
    @Test
    void deveResponder409AoBloquearVagaOcupada() throws Exception {
        CadastroService cadastro = mock(CadastroService.class);
        UUID id = UUID.randomUUID();
        when(cadastro.alterarBloqueio(eq(id), any()))
                .thenThrow(new ConflitoNegocioException("Vaga ocupada."));

        var api = MockMvcBuilders.standaloneSetup(new VagaController(cadastro))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        api.perform(patch("/vagas/" + id + "/bloqueio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloqueada\":true}"))
                .andExpect(status().isConflict());
    }
}
