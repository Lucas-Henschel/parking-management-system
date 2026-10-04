package br.furb.pagamento.controller;

import br.furb.pagamento.dto.PagamentoResponse;
import br.furb.pagamento.dto.PagarRequest;
import br.furb.pagamento.service.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamentos", description = "Operações relacionadas a pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @Operation(summary = "Pagar ticket", description = "Realiza o pagamento de um ticket previamente calculado.")
    @PostMapping("/ticket/{ticketId}/pagar")
    public ResponseEntity<PagamentoResponse> pagar(@PathVariable UUID ticketId, @RequestBody @Valid PagarRequest request) {
        return ResponseEntity.ok(pagamentoService.pagar(ticketId, request));
    }

    @Operation(summary = "Buscar pagamento por ID", description = "Retorna os detalhes de um pagamento específico.")
    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pagamentoService.buscarPorId(id));
    }

    @Operation(summary = "Buscar histórico por ticket", description = "Retorna todos os pagamentos (e tentativas) de um ticket.")
    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<List<PagamentoResponse>> buscarPorTicket(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(pagamentoService.buscarPorTicket(ticketId));
    }
}
