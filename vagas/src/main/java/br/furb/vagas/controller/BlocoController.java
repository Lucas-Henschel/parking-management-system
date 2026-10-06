package br.furb.vagas.controller;

import br.furb.vagas.dto.BlocoRequest;
import br.furb.vagas.dto.BlocoResponse;
import br.furb.vagas.service.BlocoService;

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
@RequestMapping("/blocos")
@Tag(name = "Blocos", description = "Operações relacionadas aos blocos de cada setor")
public class BlocoController {
    private final BlocoService blocoService;

    public BlocoController(BlocoService blocoService) { this.blocoService = blocoService; }

    @Operation(summary = "Listar blocos", description = "Retorna os blocos cadastrados, de forma paginada.")
    @GetMapping
    public Page<BlocoResponse> listar(Pageable paginacao) { return blocoService.listar(paginacao); }

    @Operation(summary = "Buscar bloco por ID", description = "Retorna os detalhes de um bloco específico.")
    @GetMapping("/{id}")
    public BlocoResponse consultar(@PathVariable UUID id) { return blocoService.consultar(id); }

    @Operation(summary = "Cadastrar bloco", description = "Cadastra um novo bloco em um setor existente.")
    @PostMapping
    public ResponseEntity<BlocoResponse> cadastrar(@Valid @RequestBody BlocoRequest dados) {
        BlocoResponse resposta = blocoService.cadastrar(dados);
        return ResponseEntity.created(URI.create("/blocos/" + resposta.id())).body(resposta);
    }
}
