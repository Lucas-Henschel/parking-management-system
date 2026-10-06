package br.furb.vagas.controller;

import br.furb.vagas.dto.SetorRequest;
import br.furb.vagas.dto.SetorResponse;
import br.furb.vagas.service.SetorService;

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
@RequestMapping("/setores")
@Tag(name = "Setores", description = "Operações relacionadas aos setores do estacionamento")
public class SetorController {
    private final SetorService setorService;

    public SetorController(SetorService setorService) { this.setorService = setorService; }

    @Operation(summary = "Listar setores", description = "Retorna os setores cadastrados, de forma paginada.")
    @GetMapping
    public Page<SetorResponse> listar(Pageable paginacao) { return setorService.listar(paginacao); }

    @Operation(summary = "Buscar setor por ID", description = "Retorna os detalhes de um setor específico.")
    @GetMapping("/{id}")
    public SetorResponse consultar(@PathVariable UUID id) { return setorService.consultar(id); }

    @Operation(summary = "Cadastrar setor", description = "Cadastra um novo setor com código único e status.")
    @PostMapping
    public ResponseEntity<SetorResponse> cadastrar(@Valid @RequestBody SetorRequest dados) {
        SetorResponse resposta = setorService.cadastrar(dados);
        return ResponseEntity.created(URI.create("/setores/" + resposta.id())).body(resposta);
    }
}
