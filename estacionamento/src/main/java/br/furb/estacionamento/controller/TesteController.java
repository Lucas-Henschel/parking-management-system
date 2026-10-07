package br.furb.estacionamento.controller;

import br.furb.estacionamento.dto.TesteResponse;
import br.furb.estacionamento.service.TesteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Teste", description = "Simulação do fluxo completo com dados reais")
public class TesteController {
    private final TesteService testeService;

    public TesteController(TesteService testeService) {
        this.testeService = testeService;
    }

    @PostMapping("/teste")
    @Operation(
        summary = "Preparar vagas e simular entradas, saídas e pagamentos",
        description = "Cria dados reais, ocupa as vagas e valida a recusa após três tentativas. Aguarda o RabbitMQ."
    )
    public ResponseEntity<TesteResponse> executar() {
        TesteResponse resultado = testeService.executar();
        return ResponseEntity.status(resultado.sucesso() ? 200 : 502).body(resultado);
    }
}
