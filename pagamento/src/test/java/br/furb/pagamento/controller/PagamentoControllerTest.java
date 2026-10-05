package br.furb.pagamento.controller;

import br.furb.pagamento.exception.GlobalExceptionHandler;
import br.furb.pagamento.exception.MetodoPagamentoInvalidoException;
import br.furb.pagamento.exception.PagamentoNaoEncontradoException;
import br.furb.pagamento.service.PagamentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PagamentoController.class)
@Import(GlobalExceptionHandler.class)
class PagamentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PagamentoService service;

    @Test
    void jsonInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/pagamentos/ticket/{id}/pagar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ metodo: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void metodoEmBrancoRetorna400ComErrosPorCampo() throws Exception {
        mockMvc.perform(post("/pagamentos/ticket/{id}/pagar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.metodo").exists());
    }

    @Test
    void ticketIdMalformadoRetorna400() throws Exception {
        mockMvc.perform(get("/pagamentos/ticket/nao-e-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagamentoInexistenteRetorna404() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(service.buscarPorTicket(ticketId)).thenThrow(new PagamentoNaoEncontradoException(ticketId));

        mockMvc.perform(get("/pagamentos/ticket/{id}", ticketId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pagamento não encontrado"));
    }

    @Test
    void metodoHttpNaoSuportadoRetorna405() throws Exception {
        mockMvc.perform(post("/pagamentos/{id}", UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void conflitoDeEstadoRetorna409() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(service.pagar(any(), any())).thenThrow(new IllegalStateException("estado inválido"));

        mockMvc.perform(post("/pagamentos/ticket/{id}/pagar", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\": \"PIX\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void metodoInvalidoRetorna400ComMetodoRecebido() throws Exception {
        when(service.pagar(any(), any())).thenThrow(new MetodoPagamentoInvalidoException("BITCOIN"));

        mockMvc.perform(post("/pagamentos/ticket/{id}/pagar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\": \"BITCOIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Método de pagamento inválido"))
                .andExpect(jsonPath("$.metodo").value("BITCOIN"));
    }
}
