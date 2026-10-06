package br.furb.vagas.service;

import br.furb.vagas.dto.TipoVagaRequest;
import br.furb.vagas.dto.TipoVagaResponse;
import br.furb.vagas.entity.TipoVaga;
import br.furb.vagas.enums.Recurso;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.repository.TipoVagaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TipoVagaService {
    private final TipoVagaRepository tipoVagaRepository;

    public TipoVagaService(TipoVagaRepository tipoVagaRepository) {
        this.tipoVagaRepository = tipoVagaRepository;
    }

    @Transactional(readOnly = true)
    public Page<TipoVagaResponse> listar(Pageable paginacao) {
        return tipoVagaRepository.findAll(paginacao).map(TipoVagaResponse::de);
    }

    @Transactional(readOnly = true)
    public TipoVagaResponse consultar(UUID id) {
        TipoVaga tipo = tipoVagaRepository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(Recurso.TIPO_VAGA));

        return TipoVagaResponse.de(tipo);
    }

    @Transactional
    public TipoVagaResponse cadastrar(TipoVagaRequest dados) {
        return TipoVagaResponse.de(tipoVagaRepository.save(new TipoVaga(dados.nome().strip())));
    }
}
