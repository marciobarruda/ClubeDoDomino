require('dotenv').config();
const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const axios = require('axios');
const bcrypt = require('bcryptjs');
const cron = require('node-cron');
const fs = require('fs');
const path = require('path');

const BCRYPT_ROUNDS = 10;

// E-mail autorizado a trocar a senha de acesso ao MySQL pelo app (módulo Administração).
const DB_PASSWORD_ADMIN_EMAIL = 'marciobarruda@gmail.com';

// Persistência local da senha do banco, para sobreviver a restarts do processo/container
// (ex: crash recovery, `docker restart`). Um rebuild de imagem sem volume externo apaga este
// arquivo — nesse caso o servidor volta a usar o DB_PASS do .env até a senha ser definida de novo.
const DB_PASS_OVERRIDE_FILE = path.join(__dirname, '.db-pass-override.json');

const readPersistedDbPassword = () => {
  try {
    const raw = fs.readFileSync(DB_PASS_OVERRIDE_FILE, 'utf8');
    return JSON.parse(raw).password || null;
  } catch (e) {
    return null;
  }
};

const persistDbPassword = (password) => {
  fs.writeFileSync(
    DB_PASS_OVERRIDE_FILE,
    JSON.stringify({ password, updatedAt: new Date().toISOString() }),
    { mode: 0o600 }
  );
};

// Verifica se a senha é um hash bcrypt (começa com $2b$ ou $2a$)
const isBcryptHash = (s) => s && (s.startsWith('$2b$') || s.startsWith('$2a$'));

const app = express();
const port = process.env.PORT || 3000;

// Configuração do middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

let currentDbPassword = readPersistedDbPassword() || process.env.DB_PASS;

// Pool de conexão com o MySQL — mutável para permitir troca de senha em runtime (ver /webhook/admin/atualizar-senha-db)
let pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: parseInt(process.env.DB_PORT) || 3306,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: currentDbPassword,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

// Testar conexão inicial com o banco de dados e garantir tabelas auxiliares
(async () => {
  try {
    const connection = await pool.getConnection();
    console.log('✅ Conexão com o banco de dados MySQL estabelecida com sucesso.');
    connection.release();

    await pool.query(
      `CREATE TABLE IF NOT EXISTS partidas_em_andamento (
        id VARCHAR(50) PRIMARY KEY,
        jogador1 VARCHAR(100) NOT NULL,
        jogador2 VARCHAR(100) NOT NULL,
        jogador3 VARCHAR(100) NOT NULL,
        jogador4 VARCHAR(100) NOT NULL,
        cadastrador VARCHAR(100) NOT NULL,
        score1 INT NOT NULL DEFAULT 0,
        score2 INT NOT NULL DEFAULT 0,
        fechas INT NOT NULL DEFAULT 0,
        data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
      )`
    );
    // Colunas de placar adicionadas depois da criação inicial da tabela — ALTER idempotente
    // para instalações que já tinham a tabela sem elas (MySQL não suporta "ADD COLUMN IF NOT
    // EXISTS" em todas as versões usadas aqui, então ignoramos o erro de coluna duplicada).
    for (const coluna of ['score1 INT NOT NULL DEFAULT 0', 'score2 INT NOT NULL DEFAULT 0', 'fechas INT NOT NULL DEFAULT 0']) {
      try {
        await pool.query(`ALTER TABLE partidas_em_andamento ADD COLUMN ${coluna}`);
      } catch (e) {
        if (!/Duplicate column/i.test(e.message)) throw e;
      }
    }
  } catch (error) {
    console.error('❌ Falha ao conectar ao banco de dados MySQL:', error.message);
  }
})();

// Helper para obter partes da data no fuso de São Paulo / Recife
const getSaoPauloDateParts = (date = new Date()) => {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
  const parts = formatter.formatToParts(date);
  const month = parts.find(p => p.type === 'month').value;
  const day = parts.find(p => p.type === 'day').value;
  const year = parts.find(p => p.type === 'year').value;
  return {
    year: parseInt(year),
    month: parseInt(month),
    day: parseInt(day)
  };
};

const getMatchDateParts = (dataStr) => {
  if (!dataStr) return null;
  let date;
  if (dataStr.includes('T')) {
    date = new Date(dataStr);
  } else {
    const parts = dataStr.split('-');
    if (parts.length === 3) {
      date = new Date(parts[0], parts[1] - 1, parts[2]);
    } else {
      date = new Date(dataStr);
    }
  }
  if (isNaN(date.getTime())) return null;
  return getSaoPauloDateParts(date);
};

// Gera a mensalidade do mês corrente para todos os jogadores ativos (exceto os de férias e o "não membro"),
// caso ainda não exista. Idempotente — pode ser chamada no cron mensal e também no boot do servidor
// para cobrir o caso do processo estar fora do ar exatamente na virada do mês.
const gerarMensalidadesDoMesAtual = async () => {
  const { year, month } = getSaoPauloDateParts();
  const mesReferencia = `${year}-${String(month).padStart(2, '0')}-01`;

  try {
    const [jogadores] = await pool.query(
      "SELECT jogador FROM jogadores WHERE (ativo IS NULL OR ativo = 1) AND (ferias IS NULL OR ferias = 0) AND jogador NOT LIKE '%NÃO MEMBRO%'"
    );

    if (jogadores.length === 0) return;

    const [existentes] = await pool.query(
      'SELECT jogador FROM mensalidades WHERE mensalidade = ?',
      [mesReferencia]
    );
    const jaGerados = new Set(existentes.map(r => (r.jogador || '').trim().toUpperCase()));

    const pendentes = jogadores
      .map(r => (r.jogador || '').trim())
      .filter(nome => nome && !jaGerados.has(nome.toUpperCase()));

    if (pendentes.length === 0) {
      console.log(`ℹ️ Mensalidades de ${mesReferencia} já geradas para todos os jogadores ativos.`);
      return;
    }

    for (const jogador of pendentes) {
      await pool.query(
        "INSERT INTO mensalidades (mensalidade, jogador, pago, createdAt, updatedAt) VALUES (?, ?, 'false', NOW(), NOW())",
        [mesReferencia, jogador]
      );
    }

    console.log(`✅ Mensalidades de ${mesReferencia} geradas para ${pendentes.length} jogador(es): ${pendentes.join(', ')}`);
  } catch (error) {
    console.error('❌ Erro ao gerar mensalidades automáticas do mês:', error.message);
  }
};

// 1. POST /webhook/login
app.post('/webhook/login', async (req, res) => {
  const { email, senha } = req.body;
  if (!email || !senha) {
    return res.status(400).json({ status: 'error', message: 'E-mail e senha são obrigatórios.' });
  }

  try {
    const [rows] = await pool.query(
      'SELECT email, senha FROM jogadores WHERE email = ?',
      [email.trim()]
    );

    if (rows.length === 0) {
      return res.status(401).json({ status: 'error', message: 'E-mail ou senha incorretos.' });
    }

    const stored = rows[0].senha ? rows[0].senha.trim() : '';
    let valid = false;

    if (isBcryptHash(stored)) {
      valid = await bcrypt.compare(senha.trim(), stored);
    } else {
      // Senha ainda em texto puro — compara e já migra para hash
      valid = stored === senha.trim();
      if (valid) {
        const hash = await bcrypt.hash(senha.trim(), BCRYPT_ROUNDS);
        await pool.query('UPDATE jogadores SET senha = ? WHERE email = ?', [hash, email.trim()]);
      }
    }

    if (valid) {
      return res.json({ status: 'success' });
    } else {
      return res.status(401).json({ status: 'error', message: 'E-mail ou senha incorretos.' });
    }
  } catch (error) {
    console.error('Erro no login:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro interno no servidor.' });
  }
});

// 1b. POST /webhook/reset-password
app.post('/webhook/reset-password', async (req, res) => {
  const { email, nova_senha } = req.body;
  if (!email || !nova_senha) {
    return res.status(400).json({ status: 'error', message: 'E-mail e nova_senha são obrigatórios.' });
  }
  if (nova_senha.trim().length < 4) {
    return res.status(400).json({ status: 'error', message: 'A senha deve ter pelo menos 4 caracteres.' });
  }

  try {
    const [rows] = await pool.query('SELECT email FROM jogadores WHERE email = ?', [email.trim()]);
    if (rows.length === 0) {
      // Não revelamos se o email existe ou não por segurança
      return res.json({ status: 'success' });
    }

    const hash = await bcrypt.hash(nova_senha.trim(), BCRYPT_ROUNDS);
    await pool.query('UPDATE jogadores SET senha = ? WHERE email = ?', [hash, email.trim()]);
    return res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao resetar senha:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro interno no servidor.' });
  }
});

// 2. GET /webhook/buscar-jogadores
app.get('/webhook/buscar-jogadores', async (req, res) => {
  try {
    let rows;
    try {
      [rows] = await pool.query('SELECT jogador, avatar, email, senha, ativo, ferias FROM jogadores');
    } catch (e) {
      [rows] = await pool.query('SELECT jogador, avatar, email, senha FROM jogadores');
    }
    // Normalizar retorno para o formato esperado pelo app/PWA
    const players = rows.map(r => ({
      jogador: r.jogador ? r.jogador.trim() : '',
      avatar: r.avatar || '',
      email: r.email ? r.email.trim() : '',
      senha: '', // nunca expor hash
      ativo: r.ativo === undefined || r.ativo === null ? 1 : Number(r.ativo),
      ferias: r.ferias === undefined || r.ferias === null ? 0 : Number(r.ferias)
    }));
    res.json(players);
  } catch (error) {
    console.error('Erro ao buscar jogadores:', error.message);
    res.status(500).json({ error: 'Erro ao buscar jogadores' });
  }
});

// 2b. POST /webhook/jogador/ativo — ativa/inativa um jogador
app.post('/webhook/jogador/ativo', async (req, res) => {
  const { email, ativo } = req.body;
  if (!email || typeof ativo === 'undefined') {
    return res.status(400).json({ status: 'error', message: 'email e ativo são obrigatórios.' });
  }
  try {
    await pool.query('UPDATE jogadores SET ativo = ? WHERE email = ?', [ativo ? 1 : 0, email.trim()]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao atualizar status ativo do jogador:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao atualizar jogador.' });
  }
});

// 2c. POST /webhook/jogador/ferias — marca/desmarca jogador como de férias
app.post('/webhook/jogador/ferias', async (req, res) => {
  const { email, ferias } = req.body;
  if (!email || typeof ferias === 'undefined') {
    return res.status(400).json({ status: 'error', message: 'email e ferias são obrigatórios.' });
  }
  try {
    await pool.query('UPDATE jogadores SET ferias = ? WHERE email = ?', [ferias ? 1 : 0, email.trim()]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao atualizar férias do jogador:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao atualizar jogador.' });
  }
});

// 2d. POST /webhook/jogador/avatar — atualiza a foto de perfil (base64)
app.post('/webhook/jogador/avatar', async (req, res) => {
  const { email, avatar } = req.body;
  if (!email || !avatar) {
    return res.status(400).json({ status: 'error', message: 'email e avatar são obrigatórios.' });
  }
  try {
    await pool.query('UPDATE jogadores SET avatar = ? WHERE email = ?', [avatar, email.trim()]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao atualizar avatar:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao atualizar avatar.' });
  }
});

// Retorna a última data útil (dia de semana) do mês informado, no formato 'yyyy-MM-dd'.
const getLastBusinessDayOfMonth = (year, month) => {
  const date = new Date(year, month, 0); // dia 0 do mês seguinte = último dia do mês atual
  while (date.getDay() === 0 || date.getDay() === 6) {
    date.setDate(date.getDate() - 1);
  }
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

// Gera a "Taxa Extra" de buchos de um mês específico (já fechado) para jogadores ativos que
// jogaram menos partidas que a média do grupo naquele mês. Para cada jogador abaixo da média de
// partidas, lança um débito de buchos igual ao déficit entre a média de buchos sofridos pelo grupo
// e o que ele próprio já sofreu — complementando o valor até a média, não substituindo-o.
// Idempotente — verifica se já existe um débito do tipo "Taxa extra" para aquele jogador/mês antes de inserir.
// Retorna um resumo { mes, mediaPartidas, mediaBuchos, gerados: [{jogador, valor}] } para uso em logs/relatórios.
const gerarTaxaExtraBuchosParaMes = async (targetYear, targetMonth) => {
  const mesInicio = `${targetYear}-${String(targetMonth).padStart(2, '0')}-01`;
  const proxMesAno = targetMonth === 12 ? targetYear + 1 : targetYear;
  const proxMes = targetMonth === 12 ? 1 : targetMonth + 1;
  const mesFim = `${proxMesAno}-${String(proxMes).padStart(2, '0')}-01`;

  const [jogadoresRows] = await pool.query(
    "SELECT jogador FROM jogadores WHERE (ativo IS NULL OR ativo = 1) AND (ferias IS NULL OR ferias = 0) AND jogador NOT LIKE '%NÃO MEMBRO%'"
  );
  const jogadoresAtivos = jogadoresRows.map(r => (r.jogador || '').trim()).filter(Boolean);
  if (jogadoresAtivos.length === 0) return { mes: mesInicio, gerados: [] };

  const [partidasRows] = await pool.query(
    'SELECT jogador1, jogador2, jogador3, jogador4 FROM partidas WHERE data >= ? AND data < ?',
    [mesInicio, mesFim]
  );

  const partidasPorJogador = {};
  for (const nome of jogadoresAtivos) partidasPorJogador[nome.toUpperCase()] = 0;
  for (const r of partidasRows) {
    for (const jogador of [r.jogador1, r.jogador2, r.jogador3, r.jogador4]) {
      const nome = (jogador || '').trim().toUpperCase();
      if (nome in partidasPorJogador) partidasPorJogador[nome]++;
    }
  }

  // Exclui lançamentos de "Taxa extra" do cálculo da média: são cobranças corretivas, não
  // desempenho real do jogador no mês — incluí-las infla a média a cada execução (rodar o
  // backfill duas vezes geraria uma 2ª cobrança sobre a média já distorcida pela 1ª).
  const [buchosRows] = await pool.query(
    "SELECT jogador, valor FROM buchos WHERE data >= ? AND data < ? AND (obs IS NULL OR obs != 'Taxa extra')",
    [mesInicio, mesFim]
  );
  const buchosPorJogador = {};
  for (const nome of jogadoresAtivos) buchosPorJogador[nome.toUpperCase()] = 0;
  for (const r of buchosRows) {
    const nome = (r.jogador || '').trim().toUpperCase();
    if (nome in buchosPorJogador) buchosPorJogador[nome] += parseFloat(r.valor) || 0;
  }

  const activeCount = jogadoresAtivos.length;
  const totalPartidas = Object.values(partidasPorJogador).reduce((a, b) => a + b, 0);
  const totalBuchos = Object.values(buchosPorJogador).reduce((a, b) => a + b, 0);
  const avgMatches = totalPartidas / activeCount;
  const avgBuchos = totalBuchos / activeCount;

  const [existentesRows] = await pool.query(
    "SELECT jogador FROM buchos WHERE obs = 'Taxa extra' AND data >= ? AND data < ?",
    [mesInicio, mesFim]
  );
  const jaGerados = new Set(existentesRows.map(r => (r.jogador || '').trim().toUpperCase()));

  const lastBusinessDay = getLastBusinessDayOfMonth(targetYear, targetMonth);
  const gerados = [];

  for (const nome of jogadoresAtivos) {
    const key = nome.toUpperCase();
    if (jaGerados.has(key)) continue;

    const playerMatches = partidasPorJogador[key] || 0;
    if (playerMatches >= avgMatches) continue;

    const playerBuchosValue = buchosPorJogador[key] || 0;
    const deficit = avgBuchos - playerBuchosValue;
    if (deficit <= 0.01) continue;

    await pool.query(
      'INSERT INTO buchos (data, jogador, valor, pago, obs, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, NOW(), NOW())',
      [lastBusinessDay, nome, String(deficit.toFixed(2)), 'false', 'Taxa extra']
    );
    gerados.push({ jogador: nome, valor: Number(deficit.toFixed(2)) });
  }

  console.log(`✅ Taxa extra de buchos de ${mesInicio} gerada para ${gerados.length} jogador(es) (média de ${avgMatches.toFixed(1)} partidas / ${avgBuchos.toFixed(2)} de bucho).`);
  return { mes: mesInicio, mediaPartidas: avgMatches, mediaBuchos: avgBuchos, gerados };
};

// Wrapper usado pelo cron mensal e pelo boot do servidor: sempre calcula sobre o mês anterior
// ao atual (o mês que acabou de fechar).
const gerarTaxaExtraBuchosMesAnterior = async () => {
  const { year, month } = getSaoPauloDateParts();
  let targetYear = year;
  let targetMonth = month - 1;
  if (targetMonth === 0) { targetMonth = 12; targetYear -= 1; }

  try {
    await gerarTaxaExtraBuchosParaMes(targetYear, targetMonth);
  } catch (error) {
    console.error('❌ Erro ao gerar taxa extra de buchos do mês anterior:', error.message);
  }
};

// Gera mensalidades retroativas para um jogador, do mês de início (startDate) até o mês anterior ao atual.
const gerarMensalidadesRetroativas = async (playerName, startYear, startMonth) => {
  const [existentesRows] = await pool.query(
    'SELECT mensalidade FROM mensalidades WHERE UPPER(TRIM(jogador)) = UPPER(?)',
    [playerName]
  );
  const existentes = new Set(existentesRows.map(r => r.mensalidade));

  const { year: anoAtual, month: mesAtual } = getSaoPauloDateParts();
  // Limite: até o mês anterior ao atual (inclusive)
  let limiteAno = anoAtual;
  let limiteMes = mesAtual - 1;
  if (limiteMes === 0) { limiteMes = 12; limiteAno -= 1; }

  let cursorAno = startYear;
  let cursorMes = startMonth;

  while (cursorAno < limiteAno || (cursorAno === limiteAno && cursorMes <= limiteMes)) {
    const dateStr = `${cursorAno}-${String(cursorMes).padStart(2, '0')}-01`;
    if (!existentes.has(dateStr)) {
      await pool.query(
        "INSERT INTO mensalidades (mensalidade, jogador, pago, createdAt, updatedAt) VALUES (?, ?, 'false', NOW(), NOW())",
        [dateStr, playerName]
      );
    }
    cursorMes++;
    if (cursorMes > 12) { cursorMes = 1; cursorAno += 1; }
  }
};

// 2e. POST /webhook/criar-jogador — cadastra um novo jogador e gera mensalidades retroativas
app.post('/webhook/criar-jogador', async (req, res) => {
  const { name, email, password, avatarId, startYear, startMonth } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ status: 'error', message: 'name, email e password são obrigatórios.' });
  }

  try {
    const hash = await bcrypt.hash(password.trim(), BCRYPT_ROUNDS);
    await pool.query(
      'INSERT INTO jogadores (jogador, avatar, email, senha, ativo, ferias, createdAt, updatedAt) VALUES (?, ?, ?, ?, 1, 0, NOW(), NOW())',
      [name.trim(), avatarId || '', email.trim().toLowerCase(), hash]
    );

    if (startYear && startMonth) {
      await gerarMensalidadesRetroativas(name.trim(), parseInt(startYear), parseInt(startMonth));
    }

    res.status(201).json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao criar jogador:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao criar jogador.' });
  }
});

// 3. GET /webhook/partidas
app.get('/webhook/partidas', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id_tabela as id, data, jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts, dupla_vencedora, cadastrador FROM partidas ORDER BY id_tabela DESC'
    );

    const matches = rows.map(r => ({
      id: r.id,
      data: r.data,
      jogador1: r.jogador1,
      jogador2: r.jogador2,
      jogador3: r.jogador3,
      jogador4: r.jogador4,
      scored1: parseInt(r.scored1) || 0,
      scored2: parseInt(r.scored2) || 0,
      buchore: r.buchore === 'true' || r.buchore === '1' || r.buchore === 1 || r.buchore === true,
      pts: parseInt(r.pts) || 0,
      dupla_vencedora: r.dupla_vencedora,
      cadastrado_por: r.cadastrador
    }));

    res.json(matches);
  } catch (error) {
    console.error('Erro ao buscar partidas:', error.message);
    res.status(500).json({ error: 'Erro ao buscar partidas' });
  }
});

// 4. POST /webhook/partidas
app.post('/webhook/partidas', async (req, res) => {
  const {
    data,
    jogador1,
    jogador2,
    jogador3,
    jogador4,
    scored1,
    scored2,
    buchore,
    pts,
    dupla_vencedora,
    cadastrado_por
  } = req.body;

  try {
    const [result] = await pool.query(
      'INSERT INTO partidas (data, jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts, dupla_vencedora, cadastrador, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())',
      [
        data,
        jogador1,
        jogador2,
        jogador3,
        jogador4,
        String(scored1 || 0),
        String(scored2 || 0),
        String(buchore || false),
        String(pts || 0),
        dupla_vencedora,
        cadastrado_por
      ]
    );

    res.status(201).json({ id: result.insertId, status: 'success' });
  } catch (error) {
    console.error('Erro ao registrar partida:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao salvar partida.' });
  }
});

// 4b. PUT /webhook/partidas/:id
app.put('/webhook/partidas/:id', async (req, res) => {
  const { id } = req.params;
  const {
    data,
    jogador1,
    jogador2,
    jogador3,
    jogador4,
    scored1,
    scored2,
    buchore,
    pts,
    dupla_vencedora,
    cadastrado_por
  } = req.body;

  try {
    await pool.query(
      `UPDATE partidas SET data=?, jogador1=?, jogador2=?, jogador3=?, jogador4=?,
       scored1=?, scored2=?, buchore=?, pts=?, dupla_vencedora=?, cadastrador=?, updatedAt=NOW()
       WHERE id_tabela=?`,
      [
        data,
        jogador1,
        jogador2,
        jogador3,
        jogador4,
        String(scored1 || 0),
        String(scored2 || 0),
        String(buchore || false),
        String(pts || 0),
        dupla_vencedora,
        cadastrado_por,
        id
      ]
    );
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao atualizar partida:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao atualizar partida.' });
  }
});

// 4c. DELETE /webhook/partidas/:id
app.delete('/webhook/partidas/:id', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query('DELETE FROM partidas WHERE id_tabela = ?', [id]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao excluir partida:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao excluir partida.' });
  }
});

// 5. GET /webhook/gravar-buchos
app.get('/webhook/gravar-buchos', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id_tabela as id, data, jogador, valor, pago, placar, dupla_vencedora, dupla_perdedora, obs FROM buchos'
    );

    const buchos = rows.map(r => ({
      id: r.id,
      data: r.data,
      jogador: r.jogador,
      valor: parseFloat(r.valor) || 0.0,
      pago: r.pago === 'true' || r.pago === '1' || r.pago === 1 || r.pago === true,
      placar: r.placar,
      dupla_vencedora: r.dupla_vencedora,
      dupla_perdedora: r.dupla_perdedora,
      obs: r.obs
    }));

    res.json(buchos);
  } catch (error) {
    console.error('Erro ao buscar buchos:', error.message);
    res.status(500).json({ error: 'Erro ao buscar buchos' });
  }
});

// 6. POST /webhook/gravar-buchos
app.post('/webhook/gravar-buchos', async (req, res) => {
  const {
    data,
    jogador,
    valor,
    pago,
    placar,
    dupla_vencedora,
    dupla_perdedora,
    obs
  } = req.body;

  try {
    const [result] = await pool.query(
      'INSERT INTO buchos (data, jogador, valor, pago, placar, dupla_vencedora, dupla_perdedora, obs, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())',
      [
        data,
        jogador,
        String(valor || 0),
        String(pago || false),
        placar,
        dupla_vencedora,
        dupla_perdedora,
        obs || ''
      ]
    );

    res.status(201).json({ id: result.insertId, status: 'success' });
  } catch (error) {
    console.error('Erro ao registrar bucho:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao registrar bucho.' });
  }
});

// 6b. DELETE /webhook/gravar-buchos/:id
app.delete('/webhook/gravar-buchos/:id', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query('DELETE FROM buchos WHERE id_tabela = ?', [id]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao excluir bucho:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao excluir bucho.' });
  }
});

// 6c. POST /webhook/gravar-buchos/:id/pagar
app.post('/webhook/gravar-buchos/:id/pagar', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query("UPDATE buchos SET pago = 'true' WHERE id_tabela = ?", [id]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao marcar bucho como pago:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao marcar bucho como pago.' });
  }
});

// 7. GET /webhook/buscar-info-mensalidade
app.get('/webhook/buscar-info-mensalidade', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id_tabela as id, mensalidade, jogador, pago FROM mensalidades'
    );

    const mensalidades = rows.map(r => ({
      id: r.id,
      mensalidade: r.mensalidade,
      jogador: r.jogador,
      pago: r.pago === 'true' || r.pago === '1' || r.pago === 1 || r.pago === true
    }));

    res.json(mensalidades);
  } catch (error) {
    console.error('Erro ao buscar mensalidades:', error.message);
    res.status(500).json({ error: 'Erro ao buscar mensalidades' });
  }
});

// 8. POST /webhook/buscar-info-mensalidade
app.post('/webhook/buscar-info-mensalidade', async (req, res) => {
  const { jogador, data_vencimento } = req.body;
  if (!jogador || !data_vencimento) {
    return res.status(400).json({ error: 'jogador e data_vencimento são obrigatórios.' });
  }

  try {
    const [existing] = await pool.query(
      'SELECT id_tabela as id, mensalidade, jogador, pago FROM mensalidades WHERE UPPER(TRIM(jogador)) = UPPER(?) AND mensalidade = ?',
      [jogador.trim(), data_vencimento.trim()]
    );

    if (existing.length > 0) {
      const row = existing[0];
      return res.json({
        id: row.id,
        mensalidade: row.mensalidade,
        jogador: row.jogador,
        pago: row.pago === 'true' || row.pago === '1' || row.pago === 1 || row.pago === true
      });
    }

    const [result] = await pool.query(
      "INSERT INTO mensalidades (mensalidade, jogador, pago, createdAt, updatedAt) VALUES (?, ?, 'false', NOW(), NOW())",
      [data_vencimento.trim(), jogador.trim()]
    );

    res.status(201).json({
      id: result.insertId,
      mensalidade: data_vencimento.trim(),
      jogador: jogador.trim(),
      pago: false
    });
  } catch (error) {
    console.error('Erro ao criar/buscar mensalidade:', error.message);
    res.status(500).json({ error: 'Erro ao processar mensalidade' });
  }
});

// 8b. DELETE /webhook/buscar-info-mensalidade/:id
app.delete('/webhook/buscar-info-mensalidade/:id', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query('DELETE FROM mensalidades WHERE id_tabela = ?', [id]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao excluir mensalidade:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao excluir mensalidade.' });
  }
});

// 8c. POST /webhook/buscar-info-mensalidade/:id/pagar
app.post('/webhook/buscar-info-mensalidade/:id/pagar', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query("UPDATE mensalidades SET pago = 'true' WHERE id_tabela = ?", [id]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao marcar mensalidade como paga:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao marcar mensalidade como paga.' });
  }
});

// 9. GET /webhook/listar-ranking
app.get('/webhook/listar-ranking', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id_tabela as id, data, jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts, dupla_vencedora FROM partidas ORDER BY id_tabela DESC'
    );

    const ignored = new Set(["ÍNDIO", "XAMÃ", "EX-MEMBRO", "JOSELITRO", "JOGADOR NÃO MEMBRO", "POLÍCIA FEMININA", "YAN"]);
    const playersStats = {};
    const todayParts = getSaoPauloDateParts();

    for (const r of rows) {
      const mParts = getMatchDateParts(r.data);
      if (!mParts) continue;

      const winners = (r.dupla_vencedora || "")
        .split(/[&/]/)
        .map(p => p.trim().toUpperCase());

      const pontos = parseInt(r.pts) || 0;

      const participants = [r.jogador1, r.jogador2, r.jogador3, r.jogador4]
        .filter(Boolean)
        .map(p => p.trim().toUpperCase())
        .filter(p => p && !ignored.has(p));

      for (const jogador of participants) {
        if (!playersStats[jogador]) {
          playersStats[jogador] = {
            jogador,
            partidas_dia: 0,
            pontos_dia: 0,
            saldo_dia: 0,
            vitorias_dia: 0,
            derrotas_dia: 0,
            partidas_mes: 0,
            pontos_mes: 0,
            partidas_ano: 0,
            pontos_ano: 0
          };
        }
        const s = playersStats[jogador];
        const isWinner = winners.includes(jogador);

        if (mParts.year === todayParts.year) {
          s.partidas_ano++;
          if (isWinner) {
            s.pontos_ano += pontos;
          }
          if (mParts.month === todayParts.month) {
            s.partidas_mes++;
            if (isWinner) {
              s.pontos_mes += pontos;
            }
            if (mParts.day === todayParts.day) {
              s.partidas_dia++;
              if (isWinner) {
                s.pontos_dia += pontos;
                s.saldo_dia += pontos;
                s.vitorias_dia++;
              } else {
                s.saldo_dia -= pontos;
                s.derrotas_dia++;
              }
            }
          }
        }
      }
    }

    const ranking = Object.values(playersStats).sort((a, b) => {
      return (b.pontos_mes - a.pontos_mes) || (b.pontos_ano - a.pontos_ano);
    });

    res.json(ranking);
  } catch (error) {
    console.error('Erro ao computar ranking:', error.message);
    res.status(500).json({ error: 'Erro ao computar ranking' });
  }
});

// ─── Notificação de comprovante no Telegram, com confirmação de baixa ────────
//
// Substitui o fluxo antigo do n8n (webhooks "receber-comprovante" e
// "baixar-pagamentos"). Quando um comprovante chega do app, primeiro é analisado
// automaticamente por IA (ver analisarComprovanteComIA mais abaixo); se aprovado
// em todos os critérios, a baixa é dada direto e o Telegram só recebe um aviso.
// Caso contrário, a imagem vai pro Telegram do responsável financeiro com botões
// "Confirmo"/"Não Confirmo". Os IDs de bucho/mensalidade daquele comprovante
// viajam dentro do callback_data do botão; ao clicar, o Telegram chama de volta
// /webhook/telegram-callback, que dá baixa no banco (reaproveitando as mesmas
// rotas de "marcar como pago" já usadas pelo app) e edita a mensagem removendo
// os botões.
//
// Requer as variáveis de ambiente TELEGRAM_BOT_TOKEN (gerado pelo @BotFather)
// e TELEGRAM_CHAT_ID (chat_id obtido após o destinatário iniciar uma conversa
// com o bot). Se não configuradas, a notificação é pulada silenciosamente —
// não bloqueia o recebimento do comprovante pelo app.

const telegramApi = (method) => `https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}/${method}`;

// O callback_data do Telegram tem limite de 64 bytes, então os IDs de bucho/mensalidade não
// cabem direto nele quando há vários. Guardamos o payload completo numa tabela (chaveada por um
// id curto, auto-incremento) e repassamos só esse id no botão — persistente, sobrevive a um
// restart do servidor entre o envio da notificação e o clique do Amilton.
const garantirTabelaPagamentosPendentes = async () => {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS pagamentos_pendentes_telegram (
      id INT AUTO_INCREMENT PRIMARY KEY,
      jogador_nome VARCHAR(255) NOT NULL,
      bucho_ids TEXT,
      mensalidade_ids TEXT,
      resolvido TINYINT(1) NOT NULL DEFAULT 0,
      createdAt DATETIME NOT NULL,
      updatedAt DATETIME NOT NULL
    )
  `);
};

const notificarComprovanteNoTelegram = async ({ jogadorNome, valorTotal, buchoIds, mensalidadeIds, imagemBase64, statusJaResolvido, avisoValidacao }) => {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  if (!token || !chatId) {
    console.warn('⚠️ TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID não configurados — notificação de comprovante pulada.');
    return;
  }

  const valorFormatado = (valorTotal || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const agora = new Date().toLocaleString('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
  let caption = `🧾 *Comprovante recebido!*\n\n👤 *Jogador:* ${jogadorNome}\n💵 *Valor:* R$ ${valorFormatado}\n🕒 ${agora}`;

  // Já resolvido pela baixa automática por IA — só avisa, sem pedir decisão.
  if (statusJaResolvido) {
    caption += `\n\n${statusJaResolvido}`;
    try {
      if (imagemBase64) {
        const base64Data = imagemBase64.includes(',') ? imagemBase64.split(',')[1] : imagemBase64;
        const buffer = Buffer.from(base64Data, 'base64');
        const form = new FormData();
        form.append('chat_id', chatId);
        form.append('caption', caption);
        form.append('parse_mode', 'Markdown');
        form.append('photo', new Blob([buffer], { type: 'image/jpeg' }), 'comprovante.jpg');
        const response = await fetch(telegramApi('sendPhoto'), { method: 'POST', body: form });
        if (!response.ok) console.error('❌ Erro ao notificar comprovante no Telegram:', await response.text());
      } else {
        const response = await fetch(telegramApi('sendMessage'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ chat_id: chatId, text: caption, parse_mode: 'Markdown' })
        });
        if (!response.ok) console.error('❌ Erro ao notificar comprovante no Telegram:', await response.text());
      }
    } catch (error) {
      console.error('❌ Erro ao notificar comprovante no Telegram:', error.message);
    }
    return;
  }

  if (avisoValidacao) {
    caption += `\n\n⚠️ *Verificação automática não aprovou a baixa:*\n${avisoValidacao}\n\n_Revise o comprovante e decida manualmente:_`;
  }

  await garantirTabelaPagamentosPendentes();
  const [result] = await pool.query(
    'INSERT INTO pagamentos_pendentes_telegram (jogador_nome, bucho_ids, mensalidade_ids, createdAt, updatedAt) VALUES (?, ?, ?, NOW(), NOW())',
    [jogadorNome, JSON.stringify(buchoIds || []), JSON.stringify(mensalidadeIds || [])]
  );
  const pagamentoId = result.insertId;

  const replyMarkup = JSON.stringify({
    inline_keyboard: [[
      { text: '❌ Não Confirmo', callback_data: `NAO|${pagamentoId}` },
      { text: 'Confirmo ✅', callback_data: `SIM|${pagamentoId}` }
    ]]
  });

  try {
    if (imagemBase64) {
      // Remove o prefixo data URI (ex: "data:image/png;base64,") caso venha incluído.
      const base64Data = imagemBase64.includes(',') ? imagemBase64.split(',')[1] : imagemBase64;
      const buffer = Buffer.from(base64Data, 'base64');

      // FormData/Blob/fetch nativos do Node (18+) — evita depender do pacote "form-data".
      const form = new FormData();
      form.append('chat_id', chatId);
      form.append('caption', caption);
      form.append('parse_mode', 'Markdown');
      form.append('reply_markup', replyMarkup);
      form.append('photo', new Blob([buffer], { type: 'image/jpeg' }), 'comprovante.jpg');

      const response = await fetch(telegramApi('sendPhoto'), { method: 'POST', body: form });
      if (!response.ok) {
        console.error('❌ Erro ao notificar comprovante no Telegram:', await response.text());
      }
    } else {
      const response = await fetch(telegramApi('sendMessage'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ chat_id: chatId, text: caption, parse_mode: 'Markdown', reply_markup: replyMarkup })
      });
      if (!response.ok) {
        console.error('❌ Erro ao notificar comprovante no Telegram:', await response.text());
      }
    }
  } catch (error) {
    console.error('❌ Erro ao notificar comprovante no Telegram:', error.message);
  }
};

// ─── Análise automática de comprovante via IA (Groq Vision) ──────────────────
//
// Antes de decidir entre dar baixa automática ou cair no fluxo manual do Telegram,
// o comprovante é submetido a um modelo de visão (Llama 4 Scout, via Groq) que avalia:
// 1) se a imagem é de fato um comprovante bancário/Pix (não uma foto qualquer);
// 2) se há algum código/selo de autenticação visível nele;
// 3) quem é o credor/destinatário do pagamento;
// 4) a data do pagamento;
// 5) o valor pago.
//
// A baixa automática só acontece se TODOS os critérios abaixo forem satisfeitos:
// documento parece um comprovante bancário genuíno, tem autenticação, credor é o
// Amilton, data é de hoje (ou ontem, por tolerância de fuso/horário de processamento),
// e valor pago >= valor total devido. Qualquer dúvida cai no fluxo manual existente
// (notificação no Telegram com botões Confirmo/Não Confirmo).
//
// Requer a env var GROQ_API_KEY (gratuita em https://console.groq.com). Se não
// configurada, ou se a análise falhar por qualquer motivo, o sistema cai com
// segurança no fluxo manual — nunca trava o recebimento do comprovante.

const garantirTabelaComprovantes = async () => {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS comprovantes_submetidos (
      id_tabela INT AUTO_INCREMENT PRIMARY KEY,
      jogador_nome VARCHAR(255) NOT NULL,
      valor_esperado DECIMAL(10,2) NOT NULL,
      bucho_ids TEXT,
      mensalidade_ids TEXT,
      parece_comprovante_bancario TINYINT(1) DEFAULT NULL,
      possui_autenticacao TINYINT(1) DEFAULT NULL,
      credor_detectado VARCHAR(255) DEFAULT NULL,
      data_detectada VARCHAR(50) DEFAULT NULL,
      valor_detectado DECIMAL(10,2) DEFAULT NULL,
      analise_bruta TEXT,
      decisao VARCHAR(30) NOT NULL,
      motivo VARCHAR(255) DEFAULT NULL,
      createdAt DATETIME NOT NULL
    )
  `);
};

// Chama o Groq Vision e devolve um objeto com os campos extraídos, ou null se a
// análise não puder ser concluída (API não configurada, erro de rede, resposta
// inválida) — nesse caso o chamador deve tratar como "não deu pra confirmar".
const analisarComprovanteComIA = async (imagemBase64) => {
  const apiKey = process.env.GROQ_API_KEY;
  if (!apiKey || !imagemBase64) return null;

  const base64Data = imagemBase64.includes(',') ? imagemBase64.split(',')[1] : imagemBase64;
  const dataUrl = `data:image/jpeg;base64,${base64Data}`;

  const prompt = `Você é um analista financeiro que verifica comprovantes de pagamento (Pix, TED, boleto, transferência bancária) antes de uma baixa automática de débito. Analise a imagem anexa e responda SOMENTE com um JSON válido, sem nenhum texto antes ou depois, no formato exato:
{
  "parece_comprovante_bancario": true ou false,
  "possui_autenticacao": true ou false,
  "credor": "nome do destinatário/favorecido do pagamento, como aparece no comprovante, ou null",
  "data_pagamento": "data do pagamento no formato YYYY-MM-DD, ou null se ilegível",
  "valor_pago": valor numérico pago (apenas número, sem símbolo de moeda), ou null se ilegível
}

Critérios:
- "parece_comprovante_bancario": true somente se a imagem claramente é a tela/impressão de um comprovante de transação bancária ou Pix real (tem elementos como nome do banco, valor, data, identificador da transação). Uma foto aleatória, print de conversa, ou documento não-financeiro deve ser false.
- "possui_autenticacao": true somente se houver algum código de autenticação, ID de transação, hash, ou "autenticação" visível no documento (comprovantes bancários legítimos quase sempre têm isso).
- Seja criterioso: na dúvida sobre qualquer campo, prefira valores conservadores (false/null) a arriscar um falso positivo.`;

  try {
    const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: 'meta-llama/llama-4-scout-17b-16e-instruct',
        messages: [{
          role: 'user',
          content: [
            { type: 'text', text: prompt },
            { type: 'image_url', image_url: { url: dataUrl } }
          ]
        }],
        temperature: 0,
        response_format: { type: 'json_object' }
      })
    });

    if (!response.ok) {
      console.error('❌ Groq Vision retornou erro:', await response.text());
      return null;
    }

    const json = await response.json();
    const content = json?.choices?.[0]?.message?.content;
    if (!content) return null;

    const parsed = JSON.parse(content);
    return {
      parece_comprovante_bancario: parsed.parece_comprovante_bancario === true,
      possui_autenticacao: parsed.possui_autenticacao === true,
      credor: typeof parsed.credor === 'string' ? parsed.credor.trim() : null,
      data_pagamento: typeof parsed.data_pagamento === 'string' ? parsed.data_pagamento.trim() : null,
      valor_pago: typeof parsed.valor_pago === 'number' ? parsed.valor_pago : null,
      bruto: content
    };
  } catch (error) {
    console.error('❌ Erro ao analisar comprovante com IA:', error.message);
    return null;
  }
};

// Nome(s) aceitos como credor — comparação tolerante a acento/maiúsculas e a
// variações comuns (ex.: "Amilton Silva", "AMILTON").
const normalizarTexto = (txt) =>
  (txt || '')
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase().trim();

// Compara só o PRIMEIRO NOME do credor detectado contra os nomes esperados — não o
// texto inteiro. Assim "Amilton Silva", "AMILTON S." ou "José Amilton" (primeiro
// nome "José") são tratados corretamente, e nomes parecidos tipo "Amiltonia" não
// passam por engano (o que aconteceria com uma simples busca por substring).
const credorEhValido = (credorDetectado) => {
  const nome = normalizarTexto(credorDetectado);
  if (!nome) return false;
  const primeiroNome = nome.split(/\s+/)[0];
  const nomesEsperados = (process.env.CREDOR_ESPERADO_NOMES || 'amilton')
    .split(',').map(n => normalizarTexto(n)).filter(Boolean);
  return nomesEsperados.includes(primeiroNome);
};

const dataEhRecente = (dataStr) => {
  if (!dataStr || !/^\d{4}-\d{2}-\d{2}$/.test(dataStr)) return false;
  const detectada = new Date(`${dataStr}T00:00:00-03:00`);
  if (isNaN(detectada.getTime())) return false;
  const hoje = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/Sao_Paulo' }));
  hoje.setHours(0, 0, 0, 0);
  const diffDias = Math.round((hoje - detectada) / (1000 * 60 * 60 * 24));
  // Tolera o comprovante ter sido gerado ontem (processamento perto da meia-noite),
  // mas rejeita qualquer coisa mais velha que isso ou datada no futuro.
  return diffDias >= 0 && diffDias <= 1;
};

// Avalia o resultado da IA contra os critérios de baixa automática. Retorna
// { aprovado: boolean, motivo: string } — motivo é sempre preenchido, para
// registro no histórico e, se reprovado, para contexto na notificação manual.
const avaliarComprovante = (analise, valorEsperado) => {
  if (!analise) return { aprovado: false, motivo: 'Não foi possível analisar o comprovante automaticamente.' };
  if (!analise.parece_comprovante_bancario) return { aprovado: false, motivo: 'A imagem não parece ser um comprovante bancário.' };
  if (!analise.possui_autenticacao) return { aprovado: false, motivo: 'Não foi encontrado código de autenticação no comprovante.' };
  if (!credorEhValido(analise.credor)) return { aprovado: false, motivo: `Credor não confere (detectado: "${analise.credor || 'não identificado'}").` };
  if (!dataEhRecente(analise.data_pagamento)) return { aprovado: false, motivo: `Data do comprovante não é de hoje (detectada: "${analise.data_pagamento || 'não identificada'}").` };
  if (typeof analise.valor_pago !== 'number' || analise.valor_pago < valorEsperado - 0.01) {
    return { aprovado: false, motivo: `Valor pago (${analise.valor_pago ?? 'não identificado'}) é menor que o devido (${valorEsperado}).` };
  }
  return { aprovado: true, motivo: 'Comprovante validado automaticamente.' };
};

// Dá baixa nos débitos pelos IDs informados — mesma operação usada no fluxo manual
// (callback do Telegram), reaproveitada aqui para a baixa automática via IA.
const darBaixaPorIds = async (buchoIds, mensalidadeIds) => {
  for (const buchoId of buchoIds || []) {
    await pool.query("UPDATE buchos SET pago = 'true' WHERE id_tabela = ?", [buchoId]);
  }
  for (const mensalidadeId of mensalidadeIds || []) {
    await pool.query("UPDATE mensalidades SET pago = 'true' WHERE id_tabela = ?", [mensalidadeId]);
  }
};

// 9c. POST /webhook/testar-analise-comprovante — rota de teste para o Admin do app:
// roda a mesma análise de IA usada em produção sobre uma imagem qualquer, mas NUNCA
// dá baixa em débito nenhum (nem exige bucho_ids/mensalidade_ids). Serve só para
// conferir a eficiência da IA (o que ela detecta, se aprovaria ou não) antes de
// confiar a baixa automática a comprovantes reais. Não grava em comprovantes_submetidos
// (não é um comprovante real) — fica fora da auditoria de produção.
app.post('/webhook/testar-analise-comprovante', async (req, res) => {
  const { valor_esperado, imagem_base64 } = req.body;
  if (!imagem_base64) {
    return res.status(400).json({ error: 'imagem_base64 é obrigatório.' });
  }
  try {
    const analise = await analisarComprovanteComIA(imagem_base64);
    const { aprovado, motivo } = avaliarComprovante(analise, valor_esperado || 0);
    res.json({
      aprovado,
      motivo,
      analise: analise ? {
        parece_comprovante_bancario: analise.parece_comprovante_bancario,
        possui_autenticacao: analise.possui_autenticacao,
        credor: analise.credor,
        data_pagamento: analise.data_pagamento,
        valor_pago: analise.valor_pago
      } : null
    });
  } catch (error) {
    console.error('Erro ao testar análise de comprovante:', error.message);
    res.status(500).json({ error: 'Erro ao testar análise de comprovante.' });
  }
});

// 10. POST /webhook/receber-comprovante
app.post('/webhook/receber-comprovante', async (req, res) => {
  const { jogador_nome, valor_total, bucho_ids, mensalidade_ids, imagem_base64 } = req.body;
  try {
    await garantirTabelaComprovantes();

    const analise = await analisarComprovanteComIA(imagem_base64);
    const { aprovado, motivo } = avaliarComprovante(analise, valor_total || 0);

    await pool.query(
      `INSERT INTO comprovantes_submetidos
        (jogador_nome, valor_esperado, bucho_ids, mensalidade_ids, parece_comprovante_bancario,
         possui_autenticacao, credor_detectado, data_detectada, valor_detectado, analise_bruta,
         decisao, motivo, createdAt)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())`,
      [
        jogador_nome,
        valor_total || 0,
        JSON.stringify(bucho_ids || []),
        JSON.stringify(mensalidade_ids || []),
        analise ? (analise.parece_comprovante_bancario ? 1 : 0) : null,
        analise ? (analise.possui_autenticacao ? 1 : 0) : null,
        analise?.credor || null,
        analise?.data_pagamento || null,
        analise?.valor_pago ?? null,
        analise?.bruto || null,
        aprovado ? 'BAIXA_AUTOMATICA' : 'ENVIADO_PARA_TELEGRAM',
        motivo
      ]
    );

    if (aprovado) {
      await darBaixaPorIds(bucho_ids, mensalidade_ids);
      // Notifica o Amilton por ciência — sem botões, pois a baixa já foi efetuada.
      await notificarComprovanteNoTelegram({
        jogadorNome: jogador_nome,
        valorTotal: valor_total,
        buchoIds: [],
        mensalidadeIds: [],
        imagemBase64: imagem_base64,
        statusJaResolvido: '✅ Baixa automática aprovada pela análise do comprovante.'
      });
    } else {
      console.warn(`⚠️ Comprovante de ${jogador_nome} não passou na validação automática: ${motivo}`);
      await notificarComprovanteNoTelegram({
        jogadorNome: jogador_nome,
        valorTotal: valor_total,
        buchoIds: bucho_ids,
        mensalidadeIds: mensalidade_ids,
        imagemBase64: imagem_base64,
        avisoValidacao: motivo
      });
    }

    res.json({ status: 'success', baixa_automatica: aprovado });
  } catch (error) {
    console.error('Erro ao processar comprovante:', error.message);
    res.status(500).json({ error: 'Erro ao processar comprovante.' });
  }
});

// 10a. GET /webhook/comprovantes — histórico de comprovantes submetidos, para a tela
// de auditoria no Admin do app. Mais recentes primeiro; aceita ?limit= (padrão 50, máx 200).
app.get('/webhook/comprovantes', async (req, res) => {
  try {
    await garantirTabelaComprovantes();
    const limit = Math.min(parseInt(req.query.limit) || 50, 200);
    const [rows] = await pool.query(
      `SELECT id_tabela, jogador_nome, valor_esperado, parece_comprovante_bancario,
              possui_autenticacao, credor_detectado, data_detectada, valor_detectado,
              decisao, motivo, createdAt
       FROM comprovantes_submetidos
       ORDER BY id_tabela DESC
       LIMIT ?`,
      [limit]
    );
    res.json(rows);
  } catch (error) {
    console.error('Erro ao listar comprovantes:', error.message);
    res.status(500).json({ error: 'Erro ao listar comprovantes.' });
  }
});

// 10b. POST /webhook/telegram-callback — configurado como webhook do bot do Telegram
// (ver setup em README/instruções de deploy). Recebe o clique nos botões "Confirmo"/
// "Não Confirmo" da notificação de comprovante e dá baixa no banco quando confirmado.
app.post('/webhook/telegram-callback', async (req, res) => {
  const callback = req.body?.callback_query;
  if (!callback || !callback.data) {
    return res.sendStatus(200); // outros tipos de update do Telegram — nada a fazer aqui
  }

  res.sendStatus(200); // responde já ao Telegram; o processamento continua em background

  try {
    const [decisao, pagamentoIdStr] = callback.data.split('|');
    const pagamentoId = parseInt(pagamentoIdStr);

    const chatId = callback.message.chat.id;
    const messageId = callback.message.message_id;
    const captionOriginal = callback.message.caption || '';

    await garantirTabelaPagamentosPendentes();
    const [rows] = await pool.query(
      'SELECT bucho_ids, mensalidade_ids, resolvido FROM pagamentos_pendentes_telegram WHERE id = ?',
      [pagamentoId]
    );
    const pendente = rows[0];

    if (!pendente || pendente.resolvido) {
      // Pagamento não encontrado (dado antigo demais) ou já resolvido (clique duplicado).
      if (!pendente) {
        await fetch(telegramApi('editMessageCaption'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            chat_id: chatId,
            message_id: messageId,
            caption: `${captionOriginal}\n\n⚠️ *Não foi possível processar — tente dar baixa manualmente pelo app.*`,
            parse_mode: 'Markdown'
          })
        });
      }
      return;
    }

    const buchoIds = JSON.parse(pendente.bucho_ids || '[]');
    const mensalidadeIds = JSON.parse(pendente.mensalidade_ids || '[]');

    if (decisao === 'SIM') {
      for (const buchoId of buchoIds) {
        await pool.query("UPDATE buchos SET pago = 'true' WHERE id_tabela = ?", [buchoId]);
      }
      for (const mensalidadeId of mensalidadeIds) {
        await pool.query("UPDATE mensalidades SET pago = 'true' WHERE id_tabela = ?", [mensalidadeId]);
      }
    }

    await pool.query(
      'UPDATE pagamentos_pendentes_telegram SET resolvido = 1, updatedAt = NOW() WHERE id = ?',
      [pagamentoId]
    );

    const statusTexto = decisao === 'SIM' ? '✅ Pagamento Aprovado e Baixado' : '❌ Pagamento Rejeitado';
    await fetch(telegramApi('editMessageCaption'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        message_id: messageId,
        caption: `${captionOriginal}\n\n*Status: ${statusTexto}*`,
        parse_mode: 'Markdown',
        reply_markup: JSON.stringify({ inline_keyboard: [] })
      })
    });
  } catch (error) {
    console.error('❌ Erro ao processar callback do Telegram:', error.message);
  }
});

// 11. POST /webhook/estatisticas-globais — aciona sob demanda o cálculo de taxa extra de
// buchos do mês anterior (mesma rotina do cron mensal). Idempotente, pode ser chamado
// manualmente mais de uma vez sem gerar cobranças duplicadas.
app.post('/webhook/estatisticas-globais', async (req, res) => {
  try {
    await gerarTaxaExtraBuchosMesAnterior();
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao acionar geração de taxa extra de buchos:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao gerar taxa extra de buchos.' });
  }
});

// 11b. POST /webhook/taxa-extra-retroativa — roda o cálculo de taxa extra de buchos para um
// intervalo de meses já fechados (uso administrativo, para corrigir meses em que a geração
// automática ficou parada). Idempotente — meses já processados não geram cobrança duplicada.
// Body: { anoInicio, mesInicio, anoFim, mesFim } (inclusive nas duas pontas).
app.post('/webhook/taxa-extra-retroativa', async (req, res) => {
  const { anoInicio, mesInicio, anoFim, mesFim } = req.body;
  if (!anoInicio || !mesInicio || !anoFim || !mesFim) {
    return res.status(400).json({ status: 'error', message: 'anoInicio, mesInicio, anoFim e mesFim são obrigatórios.' });
  }

  try {
    const resultados = [];
    let ano = parseInt(anoInicio);
    let mes = parseInt(mesInicio);
    const anoLimite = parseInt(anoFim);
    const mesLimite = parseInt(mesFim);

    while (ano < anoLimite || (ano === anoLimite && mes <= mesLimite)) {
      const resultado = await gerarTaxaExtraBuchosParaMes(ano, mes);
      resultados.push(resultado);
      mes++;
      if (mes > 12) { mes = 1; ano += 1; }
    }

    res.json({ status: 'success', meses: resultados });
  } catch (error) {
    console.error('Erro ao gerar taxa extra retroativa:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao gerar taxa extra retroativa.' });
  }
});

// 12. GET /webhook/partidas-em-andamento — lista partidas em andamento hoje (opcionalmente filtrando por jogador)
app.get('/webhook/partidas-em-andamento', async (req, res) => {
  const { jogador } = req.query;
  try {
    let rows;
    if (jogador) {
      [rows] = await pool.query(
        `SELECT id, jogador1, jogador2, jogador3, jogador4, cadastrador, score1, score2, fechas, data_criacao
         FROM partidas_em_andamento
         WHERE (jogador1 = ? OR jogador2 = ? OR jogador3 = ? OR jogador4 = ?)
         AND DATE(data_criacao) = CURDATE() LIMIT 1`,
        [jogador, jogador, jogador, jogador]
      );
    } else {
      [rows] = await pool.query(
        `SELECT id, jogador1, jogador2, jogador3, jogador4, cadastrador, score1, score2, fechas, data_criacao
         FROM partidas_em_andamento WHERE DATE(data_criacao) = CURDATE()`
      );
    }

    const activeMatches = rows.map(r => ({
      id: r.id,
      jogador1: r.jogador1,
      jogador2: r.jogador2,
      jogador3: r.jogador3,
      jogador4: r.jogador4,
      cadastrador: r.cadastrador,
      score1: r.score1 || 0,
      score2: r.score2 || 0,
      fechas: r.fechas || 0,
      data_criacao: r.data_criacao
    }));

    res.json(activeMatches);
  } catch (error) {
    console.error('Erro ao buscar partidas em andamento:', error.message);
    res.status(500).json({ error: 'Erro ao buscar partidas em andamento' });
  }
});

// 13. POST /webhook/partidas-em-andamento
app.post('/webhook/partidas-em-andamento', async (req, res) => {
  const { id, jogador1, jogador2, jogador3, jogador4, cadastrador } = req.body;
  if (!id) {
    return res.status(400).json({ status: 'error', message: 'id é obrigatório.' });
  }
  try {
    await pool.query(
      `INSERT INTO partidas_em_andamento (id, jogador1, jogador2, jogador3, jogador4, cadastrador, data_criacao, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())`,
      [id, jogador1, jogador2, jogador3, jogador4, cadastrador]
    );
    res.status(201).json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao iniciar partida em andamento:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao iniciar partida em andamento.' });
  }
});

// 13b. PATCH /webhook/partidas-em-andamento/:id — salva o placar parcial de uma partida ativa
// como rascunho, para não se perder se o usuário sair da tela antes de confirmar a partida.
app.patch('/webhook/partidas-em-andamento/:id', async (req, res) => {
  const { id } = req.params;
  const { score1, score2, fechas } = req.body;
  try {
    await pool.query(
      'UPDATE partidas_em_andamento SET score1 = ?, score2 = ?, fechas = ?, updated_at = NOW() WHERE id = ?',
      [score1 || 0, score2 || 0, fechas || 0, id]
    );
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao salvar placar da partida em andamento:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao salvar placar.' });
  }
});

// 14. DELETE /webhook/partidas-em-andamento/:id
app.delete('/webhook/partidas-em-andamento/:id', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query('DELETE FROM partidas_em_andamento WHERE id = ?', [id]);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao excluir partida em andamento:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao excluir partida em andamento.' });
  }
});

// Testa a nova senha em um pool isolado e, se funcionar, substitui o pool ativo e persiste
// em disco. Lança se a nova senha não conseguir conectar — nesse caso nada é alterado.
const aplicarNovaSenhaDb = async (novaSenha) => {
  let testPool;
  try {
    testPool = mysql.createPool({
      host: process.env.DB_HOST,
      port: parseInt(process.env.DB_PORT) || 3306,
      database: process.env.DB_NAME,
      user: process.env.DB_USER,
      password: novaSenha,
      waitForConnections: true,
      connectionLimit: 1,
      queueLimit: 0
    });
    const testConn = await testPool.getConnection();
    testConn.release();
  } catch (error) {
    throw new Error('Não foi possível conectar ao MySQL com a nova senha. Nenhuma alteração foi aplicada.');
  } finally {
    if (testPool) await testPool.end().catch(() => {});
  }

  persistDbPassword(novaSenha);

  const oldPool = pool;
  pool = mysql.createPool({
    host: process.env.DB_HOST,
    port: parseInt(process.env.DB_PORT) || 3306,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: novaSenha,
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
  });
  currentDbPassword = novaSenha;
  await oldPool.end().catch(() => {});
};

// 15. POST /webhook/admin/atualizar-senha-db
// Permite que apenas o e-mail autorizado troque a senha de acesso ao MySQL usada pelo
// servidor, mediante confirmação da própria senha de login (mesma validação do /webhook/login).
// Depende do login funcionar — se a senha do banco já estiver desalinhada a ponto do login
// falhar, use a rota de emergência abaixo.
app.post('/webhook/admin/atualizar-senha-db', async (req, res) => {
  const { email, senhaLogin, novaSenha } = req.body;

  if (!email || !senhaLogin || !novaSenha) {
    return res.status(400).json({ status: 'error', message: 'email, senhaLogin e novaSenha são obrigatórios.' });
  }

  if (email.trim().toLowerCase() !== DB_PASSWORD_ADMIN_EMAIL) {
    return res.status(403).json({ status: 'error', message: 'Usuário não autorizado a realizar esta operação.' });
  }

  if (novaSenha.trim().length < 4) {
    return res.status(400).json({ status: 'error', message: 'A nova senha deve ter pelo menos 4 caracteres.' });
  }

  try {
    const [rows] = await pool.query('SELECT senha FROM jogadores WHERE email = ?', [email.trim()]);
    const stored = rows[0]?.senha ? rows[0].senha.trim() : '';
    const senhaValida = isBcryptHash(stored)
      ? await bcrypt.compare(senhaLogin.trim(), stored)
      : stored === senhaLogin.trim();

    if (rows.length === 0 || !senhaValida) {
      return res.status(401).json({ status: 'error', message: 'Senha de login incorreta.' });
    }
  } catch (error) {
    console.error('Erro ao validar senha de login para troca de senha do banco:', error.message);
    return res.status(500).json({ status: 'error', message: 'Erro ao validar credenciais.' });
  }

  try {
    await aplicarNovaSenhaDb(novaSenha);
    console.log(`✅ Senha de acesso ao MySQL atualizada por ${email.trim()}.`);
    res.json({ status: 'success', message: 'Senha do banco de dados atualizada com sucesso.' });
  } catch (error) {
    console.error('Falha ao validar/aplicar nova senha do banco:', error.message);
    res.status(400).json({ status: 'error', message: error.message });
  }
});

// 15b. POST /webhook/admin/emergencia/atualizar-senha-db
// Rota de emergência: usada quando a senha do banco em uso pelo servidor está desalinhada da
// senha real (ex: alguém trocou a senha direto no MySQL) e o login parou de funcionar — nesse
// cenário a rota acima é inacessível porque depende de autenticar contra a tabela `jogadores`,
// que está inacessível. Esta rota não toca no banco para autenticar: exige apenas uma chave
// secreta fixa, guardada à parte na variável de ambiente ADMIN_SECRET_KEY (nunca no app/APK).
app.post('/webhook/admin/emergencia/atualizar-senha-db', async (req, res) => {
  const adminKey = req.get('X-Admin-Key');
  const { novaSenha } = req.body;

  if (!process.env.ADMIN_SECRET_KEY) {
    return res.status(503).json({ status: 'error', message: 'Rota de emergência não configurada no servidor (ADMIN_SECRET_KEY ausente).' });
  }

  if (!adminKey || adminKey !== process.env.ADMIN_SECRET_KEY) {
    return res.status(403).json({ status: 'error', message: 'Chave de administração inválida.' });
  }

  if (!novaSenha || novaSenha.trim().length < 4) {
    return res.status(400).json({ status: 'error', message: 'A nova senha deve ter pelo menos 4 caracteres.' });
  }

  try {
    await aplicarNovaSenhaDb(novaSenha);
    console.log('✅ Senha de acesso ao MySQL atualizada via rota de emergência.');
    res.json({ status: 'success', message: 'Senha do banco de dados atualizada com sucesso.' });
  } catch (error) {
    console.error('Falha ao validar/aplicar nova senha do banco (emergência):', error.message);
    res.status(400).json({ status: 'error', message: error.message });
  }
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok', time: new Date() });
});

// Versão atual do app — consultado pelo cliente para atualizações automáticas.
// Repassa o docs/version.json publicado no GitHub Pages, que é atualizado a cada release,
// para este endpoint nunca ficar com dados desatualizados/hardcoded novamente.
app.get('/webhook/checar-atualizacao', async (req, res) => {
  try {
    const { data } = await axios.get('https://marciobarruda.github.io/ClubeDoDomino/version.json', { timeout: 5000 });
    res.json(data);
  } catch (err) {
    console.error('Erro ao buscar version.json:', err.message);
    res.status(502).json({ error: 'Falha ao consultar informações de versão.' });
  }
});

// Cron: todo dia 1º às 00:05 (horário de Recife/São Paulo), gera a mensalidade
// do mês corrente para todos os jogadores ativos, exceto o "não membro".
cron.schedule('5 0 1 * *', gerarMensalidadesDoMesAtual, {
  timezone: 'America/Sao_Paulo'
});

// Cron: todo dia 1º às 00:10 (horário de Recife/São Paulo), gera a taxa extra de
// buchos do mês que acabou de fechar, para jogadores que jogaram abaixo da média.
cron.schedule('10 0 1 * *', gerarTaxaExtraBuchosMesAnterior, {
  timezone: 'America/Sao_Paulo'
});

// Inicialização do servidor
app.listen(port, () => {
  console.log(`🚀 Servidor rodando na porta ${port}`);
  // Checagem de segurança no boot: cobre o caso do servidor estar fora do ar
  // exatamente na virada do mês, garantindo que a mensalidade e a taxa extra do
  // mês sejam geradas assim que o processo voltar a subir.
  gerarMensalidadesDoMesAtual();
  gerarTaxaExtraBuchosMesAnterior();
});
