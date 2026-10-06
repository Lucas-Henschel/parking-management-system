package br.furb.vagas.service;

import br.furb.vagas.dto.VagaRequest;
import br.furb.vagas.dto.VagaResponse;
import br.furb.vagas.entity.Vaga;
import br.furb.vagas.enums.Recurso;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.repository.BlocoRepository;
import br.furb.vagas.repository.TipoVagaRepository;
import br.furb.vagas.repository.VagaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VagaService {
    private final VagaRepository vagaRepository;
    private final BlocoRepository blocoRepository;
    private final TipoVagaRepository tipoVagaRepository;

    public VagaService(
        VagaRepository vagaRepository,
        BlocoRepository blocoRepository,
        TipoVagaRepository tipoVagaRepository
    ) {
        this.vagaRepository = vagaRepository;
        this.blocoRepository = blocoRepository;
        this.tipoVagaRepository = tipoVagaRepository;
    }

    @Transactional(readOnly = true)
    public Page<VagaResponse> listar(Pageable paginacao) {
        return vagaRepository.findAll(paginacao).map(VagaResponse::de);
    }

    @Transactional(readOnly = true)
    public VagaResponse consultar(UUID id) {
        Vaga vaga = vagaRepository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(Recurso.VAGA));

        return VagaResponse.de(vaga);
    }

    @Transactional
    public VagaResponse cadastrar(VagaRequest dados) {
        if (!blocoRepository.existsById(dados.blocoId())) {
            throw new RecursoNaoEncontradoException(Recurso.BLOCO);
        }

        if (!tipoVagaRepository.existsById(dados.tipoId())) {
            throw new RecursoNaoEncontradoException(Recurso.TIPO_VAGA);
        }

        return VagaResponse.de(
            vagaRepository.save(new Vaga(dados.numero().strip(), dados.blocoId(), dados.tipoId()))
        );
    }
}
