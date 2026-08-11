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
        data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )`
    );
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
        "INSERT INTO mensalidades (mensalidade, jogador, pago) VALUES (?, ?, 'false')",
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

// Gera mensalidades retroativas para um jogador, do mês de início (startDate) até o mês anterior ao atual.
const gerarMensalidadesRetroativas = async (playerName, startYear, startMonth) => {
  const [existentesRows] = await pool.query(
    'SELECT mensalidade FROM mensalidades WHERE jogador = ?',
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
        "INSERT INTO mensalidades (mensalidade, jogador, pago) VALUES (?, ?, 'false')",
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
      'INSERT INTO jogadores (jogador, avatar, email, senha, ativo, ferias) VALUES (?, ?, ?, ?, 1, 0)',
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
      'INSERT INTO partidas (data, jogador1, jogador2, jogador3, jogador4, scored1, scored2, buchore, pts, dupla_vencedora, cadastrador) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
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
       scored1=?, scored2=?, buchore=?, pts=?, dupla_vencedora=?, cadastrador=?
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
      'INSERT INTO buchos (data, jogador, valor, pago, placar, dupla_vencedora, dupla_perdedora, obs) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
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
      'SELECT id_tabela as id, mensalidade, jogador, pago FROM mensalidades WHERE jogador = ? AND mensalidade = ?',
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
      "INSERT INTO mensalidades (mensalidade, jogador, pago) VALUES (?, ?, 'false')",
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
                s.vitorias_dia++;
              } else {
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

// 10. POST /webhook/receber-comprovante (Proxy para n8n original)
app.post('/webhook/receber-comprovante', async (req, res) => {
  const n8nUrl = `${process.env.N8N_BASE_URL}webhook/receber-comprovante`;
  try {
    const response = await axios.post(n8nUrl, req.body, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    res.status(response.status).send(response.data);
  } catch (error) {
    console.error('Erro ao repassar comprovante para n8n:', error.message);
    if (error.response) {
      res.status(error.response.status).send(error.response.data);
    } else {
      res.status(500).json({ error: 'Erro ao enviar comprovante para o serviço de notificação.' });
    }
  }
});

// 11. POST /webhook/estatisticas-globais (Proxy para n8n original)
app.post('/webhook/estatisticas-globais', async (req, res) => {
  const n8nUrl = `${process.env.N8N_BASE_URL}webhook/estatisticas-globais`;
  try {
    const response = await axios.post(n8nUrl, req.body, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    res.status(response.status).send(response.data);
  } catch (error) {
    console.error('Erro ao repassar estatisticas-globais para n8n:', error.message);
    if (error.response) {
      res.status(error.response.status).send(error.response.data);
    } else {
      res.status(500).json({ error: 'Erro ao acionar rotina de estatísticas no n8n.' });
    }
  }
});

// 12. GET /webhook/partidas-em-andamento — lista partidas em andamento hoje (opcionalmente filtrando por jogador)
app.get('/webhook/partidas-em-andamento', async (req, res) => {
  const { jogador } = req.query;
  try {
    let rows;
    if (jogador) {
      [rows] = await pool.query(
        `SELECT id, jogador1, jogador2, jogador3, jogador4, cadastrador, data_criacao
         FROM partidas_em_andamento
         WHERE (jogador1 = ? OR jogador2 = ? OR jogador3 = ? OR jogador4 = ?)
         AND DATE(data_criacao) = CURDATE() LIMIT 1`,
        [jogador, jogador, jogador, jogador]
      );
    } else {
      [rows] = await pool.query(
        `SELECT id, jogador1, jogador2, jogador3, jogador4, cadastrador, data_criacao
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
      `INSERT INTO partidas_em_andamento (id, jogador1, jogador2, jogador3, jogador4, cadastrador)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [id, jogador1, jogador2, jogador3, jogador4, cadastrador]
    );
    res.status(201).json({ status: 'success' });
  } catch (error) {
    console.error('Erro ao iniciar partida em andamento:', error.message);
    res.status(500).json({ status: 'error', message: 'Erro ao iniciar partida em andamento.', debug: error.message, code: error.code });
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

// Inicialização do servidor
app.listen(port, () => {
  console.log(`🚀 Servidor rodando na porta ${port}`);
  // Checagem de segurança no boot: cobre o caso do servidor estar fora do ar
  // exatamente na virada do mês, garantindo que a mensalidade do mês corrente
  // seja gerada assim que o processo voltar a subir.
  gerarMensalidadesDoMesAtual();
});
