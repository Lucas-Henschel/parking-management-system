package br.furb.pagamento.controller;

import br.furb.pagamento.dto.PagamentoResponse;
import br.furb.pagamento.service.PagamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pagamentoService.buscarPorId(id));
    }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<List<PagamentoResponse>> buscarPorTicket(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(pagamentoService.buscarPorTicket(ticketId));
    }
}
