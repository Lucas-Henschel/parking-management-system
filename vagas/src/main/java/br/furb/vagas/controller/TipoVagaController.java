package br.furb.vagas.controller;

import br.furb.vagas.dto.TipoVagaRequest;
import br.furb.vagas.dto.TipoVagaResponse;
import br.furb.vagas.service.TipoVagaService;

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
@RequestMapping("/tipos-vaga")
@Tag(name = "Tipos de vaga", description = "Operações relacionadas aos tipos de vaga")
public class TipoVagaController {
    private final TipoVagaService tipoVagaService;

    public TipoVagaController(TipoVagaService tipoVagaService) { this.tipoVagaService = tipoVagaService; }

    @Operation(summary = "Listar tipos de vaga", description = "Retorna os tipos de vaga cadastrados, de forma paginada.")
    @GetMapping
    public Page<TipoVagaResponse> listar(Pageable paginacao) { return tipoVagaService.listar(paginacao); }

    @Operation(summary = "Buscar tipo de vaga por ID", description = "Retorna os detalhes de um tipo de vaga específico.")
    @GetMapping("/{id}")
    public TipoVagaResponse consultar(@PathVariable UUID id) { return tipoVagaService.consultar(id); }

    @Operation(summary = "Cadastrar tipo de vaga", description = "Cadastra um novo tipo de vaga com nome único.")
    @PostMapping
    public ResponseEntity<TipoVagaResponse> cadastrar(@Valid @RequestBody TipoVagaRequest dados) {
        TipoVagaResponse resposta = tipoVagaService.cadastrar(dados);
        return ResponseEntity.created(URI.create("/tipos-vaga/" + resposta.id())).body(resposta);
    }
}
