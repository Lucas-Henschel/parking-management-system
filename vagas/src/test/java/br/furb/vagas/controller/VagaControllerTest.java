package br.furb.vagas.controller;

import br.furb.vagas.enums.Recurso;
import br.furb.vagas.enums.VagaStatus;
import br.furb.vagas.dto.VagaResponse;
import br.furb.vagas.exception.GlobalExceptionHandler;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.service.VagaService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VagaControllerTest {
    private final VagaService vagaService = mock(VagaService.class);
    private MockMvc api;

    @BeforeEach
    void preparar() {
        api = MockMvcBuilders.standaloneSetup(new VagaController(vagaService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void deveCadastrarComStatus201ELocalizacao() throws Exception {
        UUID id = UUID.randomUUID();
        UUID blocoId = UUID.randomUUID();
        UUID tipoId = UUID.randomUUID();
        when(vagaService.cadastrar(any()))
                .thenReturn(new VagaResponse(id, "A-1", blocoId, tipoId, VagaStatus.LIVRE, null));

        api.perform(post("/vagas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"A-1\",\"blocoId\":\"" + blocoId + "\",\"tipoId\":\"" + tipoId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/vagas/" + id))
                .andExpect(jsonPath("$.numero").value("A-1"));
    }

    @Test
    void deveResponder404QuandoBlocoNaoExiste() throws Exception {
        when(vagaService.cadastrar(any())).thenThrow(new RecursoNaoEncontradoException(Recurso.BLOCO));

        api.perform(post("/vagas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"A-1\",\"blocoId\":\"" + UUID.randomUUID()
                        + "\",\"tipoId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDeveExporRotasDeAlteracaoNemExclusao() throws Exception {
        UUID id = UUID.randomUUID();

        api.perform(put("/vagas/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        api.perform(delete("/vagas/" + id)).andExpect(status().isMethodNotAllowed());
        api.perform(patch("/vagas/" + id + "/bloqueio").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloqueada\":true}")).andExpect(status().isNotFound());
    }
}
