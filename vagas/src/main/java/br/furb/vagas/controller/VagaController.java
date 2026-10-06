package br.furb.vagas.controller;

import br.furb.vagas.dto.VagaRequest;
import br.furb.vagas.dto.VagaResponse;
import br.furb.vagas.service.VagaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vagas")
@Tag(name = "Vagas", description = "Operações relacionadas às vagas do estacionamento")
public class VagaController {
    private final VagaService vagaService;

    public VagaController(VagaService vagaService) { this.vagaService = vagaService; }

    @Operation(summary = "Listar vagas", description = "Retorna as vagas cadastradas, de forma paginada, com o status e o ticket que a ocupa.")
    @GetMapping
    public Page<VagaResponse> listar(Pageable paginacao) { return vagaService.listar(paginacao); }

    @Operation(summary = "Buscar vaga por ID", description = "Retorna os detalhes de uma vaga específica.")
    @GetMapping("/{id}")
    public VagaResponse consultar(@PathVariable UUID id) { return vagaService.consultar(id); }

    @Operation(summary = "Cadastrar vaga", description = "Cadastra uma nova vaga livre em um bloco e tipo existentes. A ocupação é feita apenas via mensagens do RabbitMQ.")
    @PostMapping
    public ResponseEntity<VagaResponse> cadastrar(@Valid @RequestBody VagaRequest dados) {
        VagaResponse resposta = vagaService.cadastrar(dados);
        return ResponseEntity.created(URI.create("/vagas/" + resposta.id())).body(resposta);
    }
}
