package br.furb.vagas.controller;

import br.furb.vagas.dto.SetorResponse;
import br.furb.vagas.entity.CadastroStatus;
import br.furb.vagas.exception.GlobalExceptionHandler;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.service.CadastroService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SetorControllerTest {
    private final CadastroService cadastro = mock(CadastroService.class);
    private MockMvc api;

    @BeforeEach
    void preparar() {
        api = MockMvcBuilders.standaloneSetup(new SetorController(cadastro))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void deveCadastrarComStatus201ELocalizacao() throws Exception {
        UUID id = UUID.randomUUID();
        when(cadastro.cadastrarSetor(any())).thenReturn(new SetorResponse(id, "A", CadastroStatus.ATIVO));

        api.perform(post("/setores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"A\",\"status\":\"ATIVO\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/setores/" + id))
                .andExpect(jsonPath("$.codigo").value("A"));
    }

    @Test
    void deveRejeitarCadastroInvalido() throws Exception {
        api.perform(post("/setores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\" \",\"status\":\"ATIVO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros").isArray());
        verifyNoInteractions(cadastro);
    }

    @Test
    void deveResponder404ParaRecursoInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(cadastro.consultarSetor(id)).thenThrow(new RecursoNaoEncontradoException("Setor"));

        api.perform(get("/setores/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Setor não encontrado."));
    }
}
