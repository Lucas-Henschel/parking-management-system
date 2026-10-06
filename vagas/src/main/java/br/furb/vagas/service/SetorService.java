package br.furb.vagas.service;

import br.furb.vagas.dto.SetorRequest;
import br.furb.vagas.dto.SetorResponse;
import br.furb.vagas.entity.Setor;
import br.furb.vagas.enums.Recurso;
import br.furb.vagas.exception.RecursoNaoEncontradoException;
import br.furb.vagas.repository.SetorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SetorService {
    private final SetorRepository setorRepository;

    public SetorService(SetorRepository setorRepository) {
        this.setorRepository = setorRepository;
    }

    @Transactional(readOnly = true)
    public Page<SetorResponse> listar(Pageable paginacao) {
        return setorRepository.findAll(paginacao).map(SetorResponse::de);
    }

    @Transactional(readOnly = true)
    public SetorResponse consultar(UUID id) {
        Setor setor = setorRepository.findById(id)
            .orElseThrow(() -> new RecursoNaoEncontradoException(Recurso.SETOR));

        return SetorResponse.de(setor);
    }

    @Transactional
    public SetorResponse cadastrar(SetorRequest dados) {
        return SetorResponse.de(setorRepository.save(new Setor(dados.codigo().strip(), dados.status())));
    }
}
