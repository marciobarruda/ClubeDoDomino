// ─── Módulo de Torneios ─────────────────────────────────────────────────────
//
// Um torneio libera o cadastro de partidas numa data/horário específicos (inclusive fora da
// janela normal do app, ex.: sábado 14:00–18:00). As partidas do torneio ficam numa tabela
// PRÓPRIA (torneio_partidas) — nunca em `partidas` — e por isso, por construção:
//   • não geram cobrança (nenhum lançamento em `buchos`);
//   • não entram no ranking do clube, no Craque/Piorzinho do dia, nem na taxa extra mensal.
// As regras de pontuação são as mesmas das partidas normais (pts calculado pelo app).
//
// Ao encerrar o torneio (manualmente pelo Admin ou automaticamente pelo cron), os títulos são
// calculados e gravados em torneio_titulos como um retrato daquele momento — é essa tabela que
// alimenta a Galeria de Troféus, preservando o histórico ano a ano mesmo que partidas normais
// sejam editadas depois.
//
// Títulos:
//   campeao_torneio  — maior pontuação no torneio (desempate: vitórias no torneio)
//   chibata_man      — maior pontuação no ano            ┐ calculados SÓ com as partidas
//   mais_viril       — mais buchos aplicados no ano      │ normais do clube (tabela `partidas`),
//   gravida_taubate  — mais buchos sofridos no ano       │ de 1º de janeiro até a data do
//   bimba_ouro       — maior aproveitamento no ano       ┘ data de corte — o torneio não conta.
// Data de corte (corte_titulos): último dia de partidas normais considerado nos títulos anuais.
// Opcional no cadastro; quando vazia, vale o dia anterior ao torneio.
//
// Cobranças antecipadas: na data de corte, após o fim da janela normal de partidas (14:00), o
// servidor gera a taxa extra de buchos do mês do corte (apurada com as partidas até o corte, de
// forma definitiva — o cron do dia 1º não reprocessa o mês) e a mensalidade do mês seguinte,
// para os jogadores poderem quitar tudo antes do torneio. Se o corte cair em dezembro, a
// mensalidade de janeiro NÃO é antecipada (é do ano seguinte). Buchos de partidas normais depois
// do corte continuam sendo cobrados normalmente, como sempre.

const TITULOS = {
  campeao_torneio: 'Campeão do Torneio',
  chibata_man: 'O Chibata Man',
  mais_viril: 'O Mais Viril',
  gravida_taubate: 'A Grávida de Taubaté',
  bimba_ouro: 'O Bimba de Ouro'
};
const ORDEM_TITULOS = Object.keys(TITULOS);

// Mesma lista de contas ignoradas do ranking do app (ClubRepository.computeRankingFromMatches).
const IGNORADOS = new Set(['ÍNDIO', 'XAMÃ', 'EX-MEMBRO', 'JOSELITRO', 'JOGADOR NÃO MEMBRO', 'POLÍCIA FEMININA', 'YAN']);
const isNaoMembro = (nome) => /N[ÃA]O MEMBRO/i.test(nome || '');
const contaNoRanking = (nome) => !!nome && !IGNORADOS.has(nome) && !isNaoMembro(nome);

// Depois do fim do torneio, partidas que já estavam em andamento ainda podem ser salvas por
// este período; passado isso (ou sem partidas abertas após o fim), o cron encerra o torneio.
const TOLERANCIA_POS_FIM_MIN = 180;

// Fim da janela normal de partidas do clube — a partir daí, no dia do corte, saem as cobranças.
const HORA_COBRANCAS_ANTECIPADAS = '14:00';

// "Agora" no fuso de Recife, como strings comparáveis lexicograficamente.
const agoraRecife = () => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Recife',
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(new Date());
  const get = (t) => parts.find(p => p.type === t).value;
  const data = `${get('year')}-${get('month')}-${get('day')}`;
  const hm = `${get('hour')}:${get('minute')}`;
  return { data, hm, dataHora: `${data} ${hm}`, dataHoraSeg: `${data} ${hm}:${get('second')}` };
};

// Soma minutos a 'yyyy-MM-dd HH:mm' (aritmética em UTC, só para deslocar o relógio de parede).
const somarMinutos = (dataHora, minutos) => {
  const [d, hm] = dataHora.split(' ');
  const [y, mo, da] = d.split('-').map(Number);
  const [h, mi] = hm.split(':').map(Number);
  const dt = new Date(Date.UTC(y, mo - 1, da, h, mi + minutos));
  const pad = (n) => String(n).padStart(2, '0');
  return `${dt.getUTCFullYear()}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())} ${pad(dt.getUTCHours())}:${pad(dt.getUTCMinutes())}`;
};

const RE_DATA = /^\d{4}-\d{2}-\d{2}$/;
const RE_HORA = /^([01]\d|2[0-3]):[0-5]\d$/;

const dataBR = (ymd) => ymd.split('-').reverse().join('/');

const isTrue = (v) => v === true || v === 1 || v === '1' || v === 'true';

const diaAnterior = (ymd) => somarMinutos(`${ymd} 00:00`, -24 * 60).slice(0, 10);
const corteDoTorneio = (t) => t.corte_titulos || diaAnterior(t.data);

const statusDoTorneio = (t, agora = agoraRecife()) => {
  if (t.finalizado_em) return 'encerrado';
  const inicio = `${t.data} ${t.hora_inicio}`;
  const fim = `${t.data} ${t.hora_fim}`;
  if (agora.dataHora < inicio) return 'agendado';
  if (agora.dataHora < fim) return 'em_andamento';
  return 'aguardando_encerramento';
};

// Os títulos do ano (vencedores e pontuações) só são revelados quando termina o horário do
// torneio. Antes disso o servidor nem envia esses dados — só os indicados, sem pontuação — para
// não vazar por nenhum cliente. O Campeão do Torneio não é segredo (o ranking do torneio é aberto).
const revelacaoEm = (t) => `${t.data} ${t.hora_fim}`;
const isRevelado = (t, agora = agoraRecife()) => agora.dataHora >= revelacaoEm(t);
const filtrarNaoRevelados = (t, titulos) =>
  isRevelado(t) ? titulos : titulos.filter(x => x.tipo === 'campeao_torneio');

const torneioToJson = (t, extra = {}) => ({
  id: t.id,
  nome: t.nome,
  data: t.data,
  horaInicio: t.hora_inicio,
  horaFim: t.hora_fim,
  status: statusDoTorneio(t),
  corteTitulos: corteDoTorneio(t),
  revelado: isRevelado(t),
  cobrancasPrevistasPara: `${corteDoTorneio(t)} ${HORA_COBRANCAS_ANTECIPADAS}`,
  cobrancasGeradasEm: t.cobrancas_geradas_em || null,
  revelacaoEm: revelacaoEm(t),
  criadoPor: t.criado_por || null,
  finalizadoEm: t.finalizado_em || null,
  ...extra
});

// Estatísticas por jogador a partir de linhas de partida (formato das tabelas partidas e
// torneio_partidas: jogador1..4, scored1, scored2, buchore, pts). Vencedor pelo placar, igual
// ao ranking do app; bucho = algum placar zerado ou bucho de ré marcado.
const computarEstatisticas = (linhas) => {
  const stats = {};
  for (const r of linhas) {
    const s1 = parseInt(r.scored1) || 0;
    const s2 = parseInt(r.scored2) || 0;
    const time1 = [r.jogador1, r.jogador2].map(n => (n || '').trim().toUpperCase());
    const time2 = [r.jogador3, r.jogador4].map(n => (n || '').trim().toUpperCase());
    const vencedores = s1 > s2 ? time1 : time2;
    const isBucho = s1 === 0 || s2 === 0 || isTrue(r.buchore);
    const pts = parseInt(r.pts) || 0;

    for (const nome of [...time1, ...time2]) {
      if (!contaNoRanking(nome)) continue;
      const s = stats[nome] || (stats[nome] = {
        jogador: nome, partidas: 0, vitorias: 0, derrotas: 0, pontos: 0, buchosAplicados: 0, buchosSofridos: 0
      });
      const venceu = vencedores.includes(nome);
      s.partidas++;
      if (venceu) { s.vitorias++; s.pontos += pts; } else { s.derrotas++; }
      if (isBucho) { if (venceu) s.buchosAplicados++; else s.buchosSofridos++; }
    }
  }
  return stats;
};

const rankingDoTorneio = (linhas) =>
  Object.values(computarEstatisticas(linhas))
    .sort((a, b) => (b.pontos - a.pontos) || (b.vitorias - a.vitorias) || (a.partidas - b.partidas) || a.jogador.localeCompare(b.jogador));

// Retorna todos os jogadores empatados no topo segundo (primario, desempate). Lista vazia se
// ninguém pontua no critério (ex.: nenhum bucho no ano → ninguém leva "O Mais Viril").
const lideres = (lista, primario, desempate) => {
  const candidatos = lista.filter(s => primario(s) > 0);
  if (candidatos.length === 0) return [];
  const melhorP = Math.max(...candidatos.map(primario));
  let topo = candidatos.filter(s => primario(s) === melhorP);
  if (desempate && topo.length > 1) {
    const melhorD = Math.max(...topo.map(desempate));
    topo = topo.filter(s => desempate(s) === melhorD);
  }
  return topo;
};

module.exports = function registrarRotasDeTorneio(app, getPool, cobrancas = {}) {
  const db = () => getPool();

  const garantirTabelas = async () => {
    await db().query(`
      CREATE TABLE IF NOT EXISTS torneios (
        id INT AUTO_INCREMENT PRIMARY KEY,
        nome VARCHAR(150) NOT NULL,
        data VARCHAR(10) NOT NULL,
        hora_inicio VARCHAR(5) NOT NULL,
        hora_fim VARCHAR(5) NOT NULL,
        criado_por VARCHAR(100) NULL,
        finalizado_em VARCHAR(19) NULL,
        corte_titulos VARCHAR(10) NULL,
        cobrancas_geradas_em VARCHAR(19) NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )`);
    await db().query(`
      CREATE TABLE IF NOT EXISTS torneio_partidas (
        id INT AUTO_INCREMENT PRIMARY KEY,
        torneio_id INT NOT NULL,
        data VARCHAR(19) NOT NULL,
        jogador1 VARCHAR(100) NOT NULL,
        jogador2 VARCHAR(100) NOT NULL,
        jogador3 VARCHAR(100) NOT NULL,
        jogador4 VARCHAR(100) NOT NULL,
        scored1 INT NOT NULL DEFAULT 0,
        scored2 INT NOT NULL DEFAULT 0,
        buchore TINYINT(1) NOT NULL DEFAULT 0,
        pts INT NOT NULL DEFAULT 0,
        dupla_vencedora VARCHAR(210) NULL,
        cadastrador VARCHAR(100) NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_torneio (torneio_id)
      )`);
    await db().query(`
      CREATE TABLE IF NOT EXISTS torneio_titulos (
        id INT AUTO_INCREMENT PRIMARY KEY,
        torneio_id INT NOT NULL,
        ano INT NOT NULL,
        tipo VARCHAR(40) NOT NULL,
        titulo VARCHAR(100) NOT NULL,
        jogador VARCHAR(100) NOT NULL,
        valor VARCHAR(60) NULL,
        detalhe VARCHAR(255) NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_torneio_titulo (torneio_id)
      )`);
    for (const coluna of ['corte_titulos VARCHAR(10) NULL', 'cobrancas_geradas_em VARCHAR(19) NULL']) {
      try {
        await db().query(`ALTER TABLE torneios ADD COLUMN ${coluna}`);
      } catch (e) {
        if (!/Duplicate column/i.test(e.message)) throw e;
      }
    }
    // Partida em andamento aberta durante um torneio carrega o id dele, para que ao salvar ela
    // vá para o torneio mesmo que o horário do torneio tenha acabado no meio da partida.
    try {
      await db().query('ALTER TABLE partidas_em_andamento ADD COLUMN torneio_id INT NULL');
    } catch (e) {
      if (!/Duplicate column/i.test(e.message)) throw e;
    }
  };

  const buscarTorneio = async (id) => {
    const [rows] = await db().query('SELECT * FROM torneios WHERE id = ?', [id]);
    return rows[0] || null;
  };

  const buscarPartidasDoTorneio = async (id) => {
    const [rows] = await db().query(
      'SELECT * FROM torneio_partidas WHERE torneio_id = ? ORDER BY id DESC', [id]
    );
    return rows;
  };

  // Calcula os 5 títulos de um torneio (sem gravar). Os 4 títulos anuais usam apenas a tabela
  // `partidas` (partidas normais) do ano da data de corte, de 1º de janeiro até ela (inclusive).
  const calcularTitulos = async (torneio) => {
    const titulos = [];
    const add = (tipo, jogadores, valor, detalhe) => {
      for (const s of jogadores) {
        titulos.push({ tipo, titulo: TITULOS[tipo], jogador: s.jogador, valor: valor(s), detalhe: detalhe ? detalhe(s) : null });
      }
    };

    // 1) Campeão do torneio
    const ranking = rankingDoTorneio(await buscarPartidasDoTorneio(torneio.id));
    add('campeao_torneio', lideres(ranking, s => s.pontos, s => s.vitorias),
      s => `${s.pontos} pts`, s => `${s.vitorias} vitórias em ${s.partidas} partidas no torneio`);

    // 2–5) Títulos anuais — só partidas normais
    const corte = corteDoTorneio(torneio);
    const ano = parseInt(corte.slice(0, 4));
    const diaSeguinte = somarMinutos(`${corte} 00:00`, 24 * 60).slice(0, 10);
    const [partidasAno] = await db().query(
      'SELECT jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts FROM partidas WHERE data >= ? AND data < ?',
      [`${ano}-01-01`, diaSeguinte]
    );
    // Jogador inativo some do Ranking do app — também não concorre aos títulos.
    const [inativos] = await db().query('SELECT jogador FROM jogadores WHERE ativo = 0');
    const nomesInativos = new Set(inativos.map(r => (r.jogador || '').trim().toUpperCase()));
    const anual = Object.values(computarEstatisticas(partidasAno)).filter(s => !nomesInativos.has(s.jogador));

    // Aproveitamento exige um mínimo de partidas, senão quem jogou 2 e ganhou 2 leva 100%:
    // metade da média de partidas no ano de quem jogou (arredondado pra cima, mínimo 1).
    const comPartidas = anual.filter(s => s.partidas > 0);
    const mediaPartidas = comPartidas.length ? comPartidas.reduce((a, s) => a + s.partidas, 0) / comPartidas.length : 0;
    const minimo = Math.max(1, Math.ceil(mediaPartidas / 2));
    const aptos = comPartidas.filter(s => s.partidas >= minimo).map(s => ({ ...s, aprov: s.vitorias / s.partidas }));

    const ateCorte = `${ano} (até ${dataBR(corte)})`;
    // Critérios dos títulos anuais: a mesma regra define o vencedor e os 3 indicados.
    const criterios = [
      { tipo: 'chibata_man', lista: anual, primario: s => s.pontos, desempate: s => s.vitorias,
        valor: s => `${s.pontos} pts`, detalhe: s => `${s.partidas} partidas em ${ateCorte}` },
      { tipo: 'mais_viril', lista: anual, primario: s => s.buchosAplicados, desempate: s => -s.partidas,
        valor: s => `${s.buchosAplicados} buchos aplicados`, detalhe: s => `${s.partidas} partidas em ${ateCorte}` },
      { tipo: 'gravida_taubate', lista: anual, primario: s => s.buchosSofridos, desempate: s => -s.partidas,
        valor: s => `${s.buchosSofridos} buchos sofridos`, detalhe: s => `${s.partidas} partidas em ${ateCorte}` },
      { tipo: 'bimba_ouro', lista: aptos, primario: s => s.aprov, desempate: s => s.partidas,
        valor: s => `${Math.round(s.aprov * 100)}%`,
        detalhe: s => `${s.vitorias}/${s.partidas} vitórias em ${ateCorte} (mín. ${minimo} partidas)` }
    ];

    const indicados = [];
    for (const c of criterios) {
      add(c.tipo, lideres(c.lista, c.primario, c.desempate), c.valor, c.detalhe);
      // Os 3 melhores pelo mesmo critério (o vencedor está sempre entre eles), devolvidos em ordem
      // alfabética e sem pontuação, para a lista não denunciar quem está na frente.
      const top3 = c.lista
        .filter(s => c.primario(s) > 0)
        .sort((a, b) => (c.primario(b) - c.primario(a)) || (c.desempate(b) - c.desempate(a)) || a.jogador.localeCompare(b.jogador))
        .slice(0, 3)
        .map(s => s.jogador)
        .sort((a, b) => a.localeCompare(b));
      indicados.push({ tipo: c.tipo, titulo: TITULOS[c.tipo], jogadores: top3 });
    }

    return { ano: parseInt(torneio.data.slice(0, 4)), titulos, indicados };
  };

  const encerrarTorneio = async (torneio) => {
    const { ano, titulos } = await calcularTitulos(torneio);
    const conn = await db().getConnection();
    try {
      await conn.beginTransaction();
      await conn.query('DELETE FROM torneio_titulos WHERE torneio_id = ?', [torneio.id]);
      for (const t of titulos) {
        await conn.query(
          'INSERT INTO torneio_titulos (torneio_id, ano, tipo, titulo, jogador, valor, detalhe) VALUES (?, ?, ?, ?, ?, ?, ?)',
          [torneio.id, ano, t.tipo, t.titulo, t.jogador, t.valor, t.detalhe]
        );
      }
      await conn.query('UPDATE torneios SET finalizado_em = ? WHERE id = ?', [agoraRecife().dataHoraSeg, torneio.id]);
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }
    console.log(`🏆 Torneio "${torneio.nome}" (#${torneio.id}) encerrado com ${titulos.length} título(s).`);
    return titulos;
  };

  const ordenarTitulos = (lista) =>
    [...lista].sort((a, b) => ORDEM_TITULOS.indexOf(a.tipo) - ORDEM_TITULOS.indexOf(b.tipo));

  const tituloToJson = (r) => ({ tipo: r.tipo, titulo: r.titulo, jogador: r.jogador, valor: r.valor, detalhe: r.detalhe, ano: r.ano });

  // Valida nome/data/horário e checa sobreposição com outro torneio no mesmo dia.
  const validarTorneio = async ({ nome, data, horaInicio, horaFim, corteTitulos }, ignorarId = null) => {
    if (!nome || !String(nome).trim()) return 'Informe o nome do torneio.';
    if (!RE_DATA.test(data || '')) return 'Data inválida (use aaaa-mm-dd).';
    if (!RE_HORA.test(horaInicio || '') || !RE_HORA.test(horaFim || '')) return 'Horário inválido (use HH:mm).';
    if (horaFim <= horaInicio) return 'O horário de término deve ser depois do início.';
    if (corteTitulos && !RE_DATA.test(corteTitulos)) return 'Data de corte inválida (use aaaa-mm-dd).';
    if (corteTitulos && corteTitulos > data) return 'A data de corte dos títulos não pode ser depois do torneio.';
    const [conflitos] = await db().query(
      'SELECT id, nome FROM torneios WHERE data = ? AND hora_inicio < ? AND hora_fim > ? AND (? IS NULL OR id <> ?)',
      [data, horaFim, horaInicio, ignorarId, ignorarId]
    );
    if (conflitos.length) return `Já existe o torneio "${conflitos[0].nome}" nesse horário.`;
    return null;
  };

  // ── Rotas ────────────────────────────────────────────────────────────────

  // Lista todos os torneios (mais recentes primeiro), com total de partidas e títulos gravados.
  // Também alimenta a Galeria de Troféus (agrupada por ano no app).
  app.get('/webhook/torneios', async (req, res) => {
    try {
      const [torneios] = await db().query('SELECT * FROM torneios ORDER BY data DESC, hora_inicio DESC');
      const [contagens] = await db().query('SELECT torneio_id, COUNT(*) AS total FROM torneio_partidas GROUP BY torneio_id');
      const [titulos] = await db().query('SELECT * FROM torneio_titulos');
      const totalPor = Object.fromEntries(contagens.map(c => [c.torneio_id, Number(c.total)]));
      res.json(torneios.map(t => torneioToJson(t, {
        totalPartidas: totalPor[t.id] || 0,
        titulos: filtrarNaoRevelados(t, ordenarTitulos(titulos.filter(x => x.torneio_id === t.id))).map(tituloToJson)
      })));
    } catch (error) {
      console.error('Erro ao listar torneios:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao listar torneios.' });
    }
  });

  // Torneio acontecendo agora (pelo relógio do servidor, fuso de Recife) — o app usa isso para
  // liberar o cadastro de partidas fora do horário normal e direcioná-las para o torneio.
  app.get('/webhook/torneios/ativo', async (req, res) => {
    try {
      const agora = agoraRecife();
      const [rows] = await db().query(
        'SELECT * FROM torneios WHERE data = ? AND finalizado_em IS NULL AND hora_inicio <= ? AND hora_fim > ? ORDER BY hora_inicio LIMIT 1',
        [agora.data, agora.hm, agora.hm]
      );
      res.json({ torneio: rows[0] ? torneioToJson(rows[0]) : null, agora: agora.dataHoraSeg });
    } catch (error) {
      console.error('Erro ao buscar torneio ativo:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao buscar torneio ativo.' });
    }
  });

  app.post('/webhook/torneios', async (req, res) => {
    const { nome, data, horaInicio, horaFim, corteTitulos, criadoPor } = req.body;
    try {
      const erro = await validarTorneio({ nome, data, horaInicio, horaFim, corteTitulos });
      if (erro) return res.status(400).json({ status: 'error', message: erro });
      const [result] = await db().query(
        'INSERT INTO torneios (nome, data, hora_inicio, hora_fim, corte_titulos, criado_por) VALUES (?, ?, ?, ?, ?, ?)',
        [String(nome).trim().slice(0, 150), data, horaInicio, horaFim, corteTitulos || null, criadoPor || null]
      );
      res.status(201).json({ status: 'success', id: result.insertId });
    } catch (error) {
      console.error('Erro ao criar torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao criar torneio.' });
    }
  });

  app.put('/webhook/torneios/:id', async (req, res) => {
    const { id } = req.params;
    const { nome, data, horaInicio, horaFim, corteTitulos } = req.body;
    try {
      const torneio = await buscarTorneio(id);
      if (!torneio) return res.status(404).json({ status: 'error', message: 'Torneio não encontrado.' });
      if (torneio.finalizado_em) return res.status(400).json({ status: 'error', message: 'Torneio já encerrado não pode ser alterado.' });
      const erro = await validarTorneio({ nome, data, horaInicio, horaFim, corteTitulos }, Number(id));
      if (erro) return res.status(400).json({ status: 'error', message: erro });
      await db().query(
        'UPDATE torneios SET nome = ?, data = ?, hora_inicio = ?, hora_fim = ?, corte_titulos = ? WHERE id = ?',
        [String(nome).trim().slice(0, 150), data, horaInicio, horaFim, corteTitulos || null, id]
      );
      res.json({ status: 'success' });
    } catch (error) {
      console.error('Erro ao atualizar torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao atualizar torneio.' });
    }
  });

  // Exclui o torneio com suas partidas e títulos (some também da Galeria de Troféus).
  app.delete('/webhook/torneios/:id', async (req, res) => {
    const { id } = req.params;
    try {
      await db().query('DELETE FROM torneio_titulos WHERE torneio_id = ?', [id]);
      await db().query('DELETE FROM torneio_partidas WHERE torneio_id = ?', [id]);
      await db().query('DELETE FROM torneios WHERE id = ?', [id]);
      res.json({ status: 'success' });
    } catch (error) {
      console.error('Erro ao excluir torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao excluir torneio.' });
    }
  });

  // Detalhe: ranking próprio do torneio, partidas e títulos. Antes de encerrar, devolve também
  // `titulosParciais` (cálculo ao vivo) para acompanhar a disputa.
  app.get('/webhook/torneios/:id', async (req, res) => {
    try {
      const torneio = await buscarTorneio(req.params.id);
      if (!torneio) return res.status(404).json({ status: 'error', message: 'Torneio não encontrado.' });
      const partidas = await buscarPartidasDoTorneio(torneio.id);
      const [titulosRows] = await db().query('SELECT * FROM torneio_titulos WHERE torneio_id = ?', [torneio.id]);
      const revelado = isRevelado(torneio);
      const calculo = (!torneio.finalizado_em || !revelado) ? await calcularTitulos(torneio) : null;
      const parciais = torneio.finalizado_em ? [] : filtrarNaoRevelados(torneio, calculo.titulos);
      res.json(torneioToJson(torneio, {
        totalPartidas: partidas.length,
        ranking: rankingDoTorneio(partidas),
        partidas: partidas.map(p => ({
          id: p.id,
          data: p.data,
          jogador1: p.jogador1,
          jogador2: p.jogador2,
          jogador3: p.jogador3,
          jogador4: p.jogador4,
          scored1: p.scored1,
          scored2: p.scored2,
          buchore: isTrue(p.buchore),
          pts: p.pts,
          dupla_vencedora: p.dupla_vencedora,
          cadastrado_por: p.cadastrador
        })),
        titulos: filtrarNaoRevelados(torneio, ordenarTitulos(titulosRows)).map(tituloToJson),
        titulosParciais: parciais,
        // Indicados (3 por título do ano, sem pontuação) — só até a revelação.
        indicados: revelado ? [] : calculo.indicados
      }));
    } catch (error) {
      console.error('Erro ao buscar torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao buscar torneio.' });
    }
  });

  // Registra uma partida do torneio. Aceita a partir do início do torneio até o encerramento
  // (o fim do horário + tolerância cobre partidas que já estavam em andamento). Nunca gera bucho.
  app.post('/webhook/torneios/:id/partidas', async (req, res) => {
    const { jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts, dupla_vencedora, cadastrado_por } = req.body;
    try {
      const torneio = await buscarTorneio(req.params.id);
      if (!torneio) return res.status(404).json({ status: 'error', message: 'Torneio não encontrado.' });
      if (torneio.finalizado_em) return res.status(400).json({ status: 'error', message: 'Este torneio já foi encerrado.' });
      const agora = agoraRecife();
      const inicio = `${torneio.data} ${torneio.hora_inicio}`;
      const limite = somarMinutos(`${torneio.data} ${torneio.hora_fim}`, TOLERANCIA_POS_FIM_MIN);
      if (agora.dataHora < inicio) return res.status(400).json({ status: 'error', message: 'O torneio ainda não começou.' });
      if (agora.dataHora > limite) return res.status(400).json({ status: 'error', message: 'O horário do torneio já terminou.' });

      const nomes = [jogador1, jogador2, jogador3, jogador4].map(n => (n || '').trim());
      if (nomes.some(n => !n) || new Set(nomes.map(n => n.toUpperCase())).size !== 4) {
        return res.status(400).json({ status: 'error', message: 'Informe 4 jogadores diferentes.' });
      }

      const [result] = await db().query(
        `INSERT INTO torneio_partidas (torneio_id, data, jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts, dupla_vencedora, cadastrador)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [torneio.id, agora.dataHoraSeg, ...nomes, parseInt(scored1) || 0, parseInt(scored2) || 0,
          isTrue(buchore) ? 1 : 0, parseInt(pts) || 0, dupla_vencedora || null, cadastrado_por || null]
      );
      res.status(201).json({ status: 'success', id: result.insertId });
    } catch (error) {
      console.error('Erro ao registrar partida do torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao salvar partida do torneio.' });
    }
  });

  app.delete('/webhook/torneios/:id/partidas/:partidaId', async (req, res) => {
    try {
      const torneio = await buscarTorneio(req.params.id);
      if (torneio && torneio.finalizado_em) {
        return res.status(400).json({ status: 'error', message: 'Torneio encerrado: reabra-o antes de excluir partidas.' });
      }
      await db().query('DELETE FROM torneio_partidas WHERE id = ? AND torneio_id = ?', [req.params.partidaId, req.params.id]);
      res.json({ status: 'success' });
    } catch (error) {
      console.error('Erro ao excluir partida do torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao excluir partida do torneio.' });
    }
  });

  // Encerra o torneio e grava os títulos. Pode ser chamado de novo num torneio já encerrado
  // para recalcular os títulos.
  app.post('/webhook/torneios/:id/encerrar', async (req, res) => {
    try {
      const torneio = await buscarTorneio(req.params.id);
      if (!torneio) return res.status(404).json({ status: 'error', message: 'Torneio não encontrado.' });
      const agora = agoraRecife();
      if (agora.dataHora < `${torneio.data} ${torneio.hora_inicio}`) {
        return res.status(400).json({ status: 'error', message: 'O torneio ainda não começou.' });
      }
      const titulos = await encerrarTorneio(torneio);
      res.json({ status: 'success', titulos: filtrarNaoRevelados(torneio, ordenarTitulos(titulos)) });
    } catch (error) {
      console.error('Erro ao encerrar torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao encerrar torneio.' });
    }
  });

  // Reabre um torneio encerrado (apaga os títulos gravados) — para corrigir partidas.
  app.post('/webhook/torneios/:id/reabrir', async (req, res) => {
    try {
      await db().query('DELETE FROM torneio_titulos WHERE torneio_id = ?', [req.params.id]);
      await db().query('UPDATE torneios SET finalizado_em = NULL WHERE id = ?', [req.params.id]);
      res.json({ status: 'success' });
    } catch (error) {
      console.error('Erro ao reabrir torneio:', error.message);
      res.status(500).json({ status: 'error', message: 'Erro ao reabrir torneio.' });
    }
  });

  // Encerramento automático: torneio cujo horário já acabou é encerrado quando não há mais
  // partida em andamento vinculada a ele, ou de qualquer forma após a tolerância.
  const encerrarTorneiosVencidos = async () => {
    try {
      const agora = agoraRecife();
      const [pendentes] = await db().query('SELECT * FROM torneios WHERE finalizado_em IS NULL AND data <= ?', [agora.data]);
      for (const t of pendentes) {
        const fim = `${t.data} ${t.hora_fim}`;
        if (agora.dataHora < fim) continue;
        const [[{ abertas }]] = await db().query(
          'SELECT COUNT(*) AS abertas FROM partidas_em_andamento WHERE torneio_id = ?', [t.id]
        );
        const passouTolerancia = agora.dataHora > somarMinutos(fim, TOLERANCIA_POS_FIM_MIN);
        if (Number(abertas) === 0 || passouTolerancia) await encerrarTorneio(t);
      }
    } catch (error) {
      console.error('❌ Erro no encerramento automático de torneios:', error.message);
    }
  };

  // Gera as cobranças antecipadas dos torneios cuja data de corte já passou das 14:00 (roda no
  // mesmo cron de 5 em 5 minutos). Cada torneio gera uma única vez (cobrancas_geradas_em).
  const gerarCobrancasAntecipadasDeTorneios = async () => {
    if (!cobrancas.gerarTaxaExtraBuchosParaMes || !cobrancas.gerarMensalidadesDoMes) return;
    try {
      const agora = agoraRecife();
      const [pendentes] = await db().query(
        'SELECT * FROM torneios WHERE finalizado_em IS NULL AND cobrancas_geradas_em IS NULL'
      );
      for (const t of pendentes) {
        const corte = corteDoTorneio(t);
        if (agora.dataHora < `${corte} ${HORA_COBRANCAS_ANTECIPADAS}`) continue;
        try {
          const [ano, mes] = corte.split('-').map(Number);
          await cobrancas.gerarTaxaExtraBuchosParaMes(ano, mes, { ate: corte, origem: `Torneio #${t.id} ${t.nome}`.slice(0, 100) });
          if (mes !== 12) {
            await cobrancas.gerarMensalidadesDoMes(ano, mes + 1);
          }
          await db().query('UPDATE torneios SET cobrancas_geradas_em = ? WHERE id = ?', [agora.dataHoraSeg, t.id]);
          console.log(`💳 Cobranças antecipadas do torneio "${t.nome}" (#${t.id}) geradas (corte ${corte}).`);
        } catch (e) {
          console.error(`❌ Erro ao gerar cobranças antecipadas do torneio #${t.id}:`, e.message);
        }
      }
    } catch (error) {
      console.error('❌ Erro na geração de cobranças antecipadas de torneios:', error.message);
    }
  };

  garantirTabelas()
    .then(() => console.log('✅ Tabelas de torneio verificadas.'))
    .catch(e => console.error('❌ Falha ao criar tabelas de torneio:', e.message));

  return { encerrarTorneiosVencidos, gerarCobrancasAntecipadasDeTorneios };
};
