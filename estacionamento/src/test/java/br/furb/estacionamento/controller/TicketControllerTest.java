package br.furb.estacionamento.controller;

import br.furb.estacionamento.enums.Recurso;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import br.furb.estacionamento.exception.GlobalExceptionHandler;
import br.furb.estacionamento.exception.RecursoNaoEncontradoException;
import br.furb.estacionamento.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketControllerTest {
    private final TicketService ticketService = mock(TicketService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TicketController(ticketService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void ticketInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketService.buscarPorId(id)).thenThrow(new RecursoNaoEncontradoException(Recurso.TICKET));

        mvc.perform(get("/tickets/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void saidaEmEstadoInvalidoRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(ticketService.registrarSaida(id)).thenThrow(new EstadoInvalidoException("O ticket não está disponível para saída"));

        mvc.perform(post("/tickets/" + id + "/saida")).andExpect(status().isConflict());
    }

    @Test
    void entradaSemPlacaRetorna400ComMapaDeErros() throws Exception {
        mvc.perform(post("/entrada").contentType(MediaType.APPLICATION_JSON).content("{\"placa\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.placa").exists());
    }
}
