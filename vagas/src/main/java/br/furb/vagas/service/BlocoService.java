package br.furb.vagas.service;

import br.furb.vagas.dto.BlocoRequest;
import br.furb.vagas.dto.BlocoResponse;
import br.furb.vagas.entity.Bloco;
import br.furb.vagas.enums.Recurso;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.repository.BlocoRepository;
import br.furb.vagas.repository.SetorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BlocoService {
    private final BlocoRepository blocoRepository;
    private final SetorRepository setorRepository;

    public BlocoService(BlocoRepository blocoRepository, SetorRepository setorRepository) {
        this.blocoRepository = blocoRepository;
        this.setorRepository = setorRepository;
    }

    @Transactional(readOnly = true)
    public Page<BlocoResponse> listar(Pageable paginacao) {
        return blocoRepository.findAll(paginacao).map(BlocoResponse::de);
    }

    @Transactional(readOnly = true)
    public BlocoResponse consultar(UUID id) {
        Bloco bloco = blocoRepository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(Recurso.BLOCO));

        return BlocoResponse.de(bloco);
    }

    @Transactional
    public BlocoResponse cadastrar(BlocoRequest dados) {
        if (!setorRepository.existsById(dados.setorId())) {
            throw new RecursoNaoEncontradoException(Recurso.SETOR);
        }

        return BlocoResponse.de(
            blocoRepository.save(new Bloco(dados.setorId(), dados.codigo().strip(), dados.status()))
        );
    }
}
