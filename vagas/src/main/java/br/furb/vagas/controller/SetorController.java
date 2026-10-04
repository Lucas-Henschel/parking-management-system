package br.furb.vagas.controller;

import br.furb.vagas.dto.*;
import br.furb.vagas.service.CadastroService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/setores")
public class SetorController {
    private final CadastroService cadastro;
    public SetorController(CadastroService cadastro) { this.cadastro = cadastro; }
    @GetMapping
    public Page<SetorResponse> listar(Pageable paginacao) { return cadastro.listarSetores(paginacao); }
    @GetMapping("/{id}")
    public SetorResponse consultar(@PathVariable UUID id) { return cadastro.consultarSetor(id); }
    @PostMapping
    public ResponseEntity<SetorResponse> cadastrar(@Valid @RequestBody SetorRequest dados) {
        SetorResponse resposta = cadastro.cadastrarSetor(dados);
        return ResponseEntity.created(URI.create("/setores/" + resposta.id())).body(resposta);
    }
    @PutMapping("/{id}")
    public SetorResponse atualizar(@PathVariable UUID id, @Valid @RequestBody SetorRequest dados) {
        return cadastro.atualizarSetor(id, dados);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        cadastro.excluirSetor(id); return ResponseEntity.noContent().build();
    }

}
