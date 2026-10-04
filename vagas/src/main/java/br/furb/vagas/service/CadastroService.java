package br.furb.vagas.service;

import br.furb.vagas.dto.*;
import br.furb.vagas.entity.*;
import br.furb.vagas.exception.*;
import br.furb.vagas.repository.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CadastroService {
    private final SetorRepository setores;
    private final BlocoRepository blocos;
    private final TipoVagaRepository tipos;
    private final VagaRepository vagas;
    public CadastroService(SetorRepository setores, BlocoRepository blocos,
                           TipoVagaRepository tipos, VagaRepository vagas) {
        this.setores = setores; this.blocos = blocos; this.tipos = tipos; this.vagas = vagas;
    }
    @Transactional(readOnly = true)
    public Page<SetorResponse> listarSetores(Pageable paginacao) { return setores.findAll(paginacao).map(SetorResponse::de); }
    @Transactional(readOnly = true)
    public SetorResponse consultarSetor(UUID id) { return SetorResponse.de(buscarSetor(id)); }
    public SetorResponse cadastrarSetor(SetorRequest dados) {
        return SetorResponse.de(setores.save(new Setor(dados.codigo().strip(), dados.status())));
    }
    public SetorResponse atualizarSetor(UUID id, SetorRequest dados) {
        Setor setor = buscarSetor(id); setor.atualizar(dados.codigo().strip(), dados.status());
        return SetorResponse.de(setor);
    }
    public void excluirSetor(UUID id) { setores.delete(buscarSetor(id)); setores.flush(); }
    @Transactional(readOnly = true)
    public Page<BlocoResponse> listarBlocos(Pageable paginacao) { return blocos.findAll(paginacao).map(BlocoResponse::de); }
    @Transactional(readOnly = true)
    public BlocoResponse consultarBloco(UUID id) { return BlocoResponse.de(buscarBloco(id)); }
    public BlocoResponse cadastrarBloco(BlocoRequest dados) {
        buscarSetor(dados.setorId());
        return BlocoResponse.de(blocos.save(new Bloco(dados.setorId(), dados.codigo().strip(), dados.status())));
    }
    public BlocoResponse atualizarBloco(UUID id, BlocoRequest dados) {
        buscarSetor(dados.setorId()); Bloco bloco = buscarBloco(id);
        bloco.atualizar(dados.setorId(), dados.codigo().strip(), dados.status()); return BlocoResponse.de(bloco);
    }
    public void excluirBloco(UUID id) { blocos.delete(buscarBloco(id)); blocos.flush(); }
    @Transactional(readOnly = true)
    public Page<TipoVagaResponse> listarTipos(Pageable paginacao) { return tipos.findAll(paginacao).map(TipoVagaResponse::de); }
    @Transactional(readOnly = true)
    public TipoVagaResponse consultarTipo(UUID id) { return TipoVagaResponse.de(buscarTipo(id)); }
    public TipoVagaResponse cadastrarTipo(TipoVagaRequest dados) {
        return TipoVagaResponse.de(tipos.save(new TipoVaga(dados.nome().strip())));
    }
    public TipoVagaResponse atualizarTipo(UUID id, TipoVagaRequest dados) {
        TipoVaga tipo = buscarTipo(id); tipo.atualizar(dados.nome().strip()); return TipoVagaResponse.de(tipo);
    }
    public void excluirTipo(UUID id) { tipos.delete(buscarTipo(id)); tipos.flush(); }
    @Transactional(readOnly = true)
    public Page<VagaResponse> listarVagas(Pageable paginacao) { return vagas.findAll(paginacao).map(VagaResponse::de); }
    @Transactional(readOnly = true)
    public VagaResponse consultarVaga(UUID id) { return VagaResponse.de(buscarVaga(id)); }
    public VagaResponse cadastrarVaga(VagaRequest dados) {
        validarReferencias(dados);
        return VagaResponse.de(vagas.save(new Vaga(dados.numero().strip(), dados.blocoId(), dados.tipoId())));
    }
    public VagaResponse atualizarVaga(UUID id, VagaRequest dados) {
        validarReferencias(dados); Vaga vaga = buscarVagaParaAlteracao(id);
        vaga.atualizar(dados.numero().strip(), dados.blocoId(), dados.tipoId()); return VagaResponse.de(vaga);
    }
    public VagaResponse alterarBloqueio(UUID id, BloqueioVagaRequest dados) {
        Vaga vaga = buscarVagaParaAlteracao(id); vaga.alterarBloqueio(dados.bloqueada()); return VagaResponse.de(vaga);
    }
    public void excluirVaga(UUID id) {
        Vaga vaga = buscarVagaParaAlteracao(id);
        if (vaga.obterStatus() == VagaStatus.OCUPADA) throw new ConflitoNegocioException("Uma vaga ocupada não pode ser excluída.");
        vagas.delete(vaga); vagas.flush();
    }
    @Transactional(readOnly = true)
    public OcupacaoResponse consultarOcupacao() {
        return new OcupacaoResponse(vagas.count(), vagas.contarDisponiveis(),
                vagas.contarPorStatus(VagaStatus.OCUPADA), vagas.contarPorStatus(VagaStatus.BLOQUEADA));
    }
    private void validarReferencias(VagaRequest dados) { buscarBloco(dados.blocoId()); buscarTipo(dados.tipoId()); }
    private Setor buscarSetor(UUID id) { return setores.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Setor")); }
    private Bloco buscarBloco(UUID id) { return blocos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Bloco")); }
    private TipoVaga buscarTipo(UUID id) { return tipos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de vaga")); }
    private Vaga buscarVaga(UUID id) { return vagas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Vaga")); }
    private Vaga buscarVagaParaAlteracao(UUID id) {
        return vagas.buscarParaAlteracao(id).orElseThrow(() -> new RecursoNaoEncontradoException("Vaga"));
    }
}
