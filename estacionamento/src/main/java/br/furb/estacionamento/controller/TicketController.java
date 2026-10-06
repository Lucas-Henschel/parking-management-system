package br.furb.estacionamento.controller;

import br.furb.estacionamento.dto.EntradaRequest;
import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping
@Tag(name = "Tickets", description = "Entrada e consulta de tickets do estacionamento")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/entrada")
    @Operation(summary = "Registrar entrada de veículo")
    public ResponseEntity<TicketResponse> registrarEntrada(@Valid @RequestBody EntradaRequest request) {
        TicketResponse ticket = ticketService.registrarEntrada(request.placa());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/tickets/{ticketId}")
                .buildAndExpand(ticket.id())
                .toUri();
        return ResponseEntity.accepted().location(location).body(ticket);
    }

    @PostMapping("/tickets/{ticketId}/saida")
    @Operation(summary = "Registrar saída e solicitar cálculo do pagamento",
            description = "Aceita a saída com 202. Consulte o ticket para acompanhar cálculo e pagamento. Repetições não geram novo cálculo.")
    public ResponseEntity<TicketResponse> registrarSaida(@PathVariable UUID ticketId) {
        return ResponseEntity.accepted().body(ticketService.registrarSaida(ticketId));
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Consultar ticket")
    public ResponseEntity<TicketResponse> buscarPorId(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(ticketService.buscarPorId(ticketId));
    }
}
