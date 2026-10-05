package br.furb.vagas.controller;

import br.furb.vagas.enums.Recurso;
import br.furb.vagas.dto.SetorResponse;
import br.furb.vagas.enums.CadastroStatus;
import br.furb.vagas.exception.ConflitoNegocioException;
import br.furb.vagas.exception.GlobalExceptionHandler;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.service.SetorService;
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
    private final SetorService setorService = mock(SetorService.class);
    private MockMvc api;

    @BeforeEach
    void preparar() {
        api = MockMvcBuilders.standaloneSetup(new SetorController(setorService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void deveCadastrarComStatus201ELocalizacao() throws Exception {
        UUID id = UUID.randomUUID();
        when(setorService.cadastrar(any())).thenReturn(new SetorResponse(id, "A", CadastroStatus.ATIVO));

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
                .andExpect(jsonPath("$.errors.codigo").exists());
        verifyNoInteractions(setorService);
    }

    @Test
    void deveResponder404ParaRecursoInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(setorService.consultar(id)).thenThrow(new RecursoNaoEncontradoException(Recurso.SETOR));

        api.perform(get("/setores/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Setor não encontrado."));
    }

    @Test
    void deveResponder400ParaJsonMalformado() throws Exception {
        api.perform(post("/setores").contentType(MediaType.APPLICATION_JSON).content("{invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
        verifyNoInteractions(setorService);
    }

    @Test
    void deveResponder405ParaMetodoNaoPermitido() throws Exception {
        api.perform(delete("/setores/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deveResponder400ParaIdInvalido() throws Exception {
        api.perform(get("/setores/nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.parking.com/errors/tipo-invalido"));
    }

    @Test
    void deveResponder409ParaConflitoDeNegocio() throws Exception {
        when(setorService.cadastrar(any())).thenThrow(new ConflitoNegocioException("Setor duplicado."));

        api.perform(post("/setores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"A\",\"status\":\"ATIVO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Setor duplicado."));
    }
}
