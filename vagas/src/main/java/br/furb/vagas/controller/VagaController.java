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
@RequestMapping("/vagas")
public class VagaController {
    private final CadastroService cadastro;
    public VagaController(CadastroService cadastro) { this.cadastro = cadastro; }
    @GetMapping
    public Page<VagaResponse> listar(Pageable paginacao) { return cadastro.listarVagas(paginacao); }
    @GetMapping("/{id}")
    public VagaResponse consultar(@PathVariable UUID id) { return cadastro.consultarVaga(id); }
    @PostMapping
    public ResponseEntity<VagaResponse> cadastrar(@Valid @RequestBody VagaRequest dados) {
        VagaResponse resposta = cadastro.cadastrarVaga(dados);
        return ResponseEntity.created(URI.create("/vagas/" + resposta.id())).body(resposta);
    }
    @PutMapping("/{id}")
    public VagaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody VagaRequest dados) {
        return cadastro.atualizarVaga(id, dados);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        cadastro.excluirVaga(id); return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/bloqueio")
    public VagaResponse alterarBloqueio(@PathVariable UUID id, @Valid @RequestBody BloqueioVagaRequest dados) {
        return cadastro.alterarBloqueio(id, dados);
    }
    @GetMapping("/ocupacao")
    public OcupacaoResponse consultarOcupacao() { return cadastro.consultarOcupacao(); }
}
