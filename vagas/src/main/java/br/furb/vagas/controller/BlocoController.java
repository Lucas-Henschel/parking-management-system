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
@RequestMapping("/blocos")
public class BlocoController {
    private final CadastroService cadastro;
    public BlocoController(CadastroService cadastro) { this.cadastro = cadastro; }
    @GetMapping
    public Page<BlocoResponse> listar(Pageable paginacao) { return cadastro.listarBlocos(paginacao); }
    @GetMapping("/{id}")
    public BlocoResponse consultar(@PathVariable UUID id) { return cadastro.consultarBloco(id); }
    @PostMapping
    public ResponseEntity<BlocoResponse> cadastrar(@Valid @RequestBody BlocoRequest dados) {
        BlocoResponse resposta = cadastro.cadastrarBloco(dados);
        return ResponseEntity.created(URI.create("/blocos/" + resposta.id())).body(resposta);
    }
    @PutMapping("/{id}")
    public BlocoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody BlocoRequest dados) {
        return cadastro.atualizarBloco(id, dados);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        cadastro.excluirBloco(id); return ResponseEntity.noContent().build();
    }

}
