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
@RequestMapping("/tipos-vaga")
public class TipoVagaController {
    private final CadastroService cadastro;
    public TipoVagaController(CadastroService cadastro) { this.cadastro = cadastro; }
    @GetMapping
    public Page<TipoVagaResponse> listar(Pageable paginacao) { return cadastro.listarTipos(paginacao); }
    @GetMapping("/{id}")
    public TipoVagaResponse consultar(@PathVariable UUID id) { return cadastro.consultarTipo(id); }
    @PostMapping
    public ResponseEntity<TipoVagaResponse> cadastrar(@Valid @RequestBody TipoVagaRequest dados) {
        TipoVagaResponse resposta = cadastro.cadastrarTipo(dados);
        return ResponseEntity.created(URI.create("/tipos-vaga/" + resposta.id())).body(resposta);
    }
    @PutMapping("/{id}")
    public TipoVagaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody TipoVagaRequest dados) {
        return cadastro.atualizarTipo(id, dados);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        cadastro.excluirTipo(id); return ResponseEntity.noContent().build();
    }

}
