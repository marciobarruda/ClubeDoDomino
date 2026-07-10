require('dotenv').config();
const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const axios = require('axios');
const bcrypt = require('bcryptjs');

const BCRYPT_ROUNDS = 10;

// Verifica se a senha é um hash bcrypt (começa com $2b$ ou $2a$)
const isBcryptHash = (s) => s && (s.startsWith('$2b$') || s.startsWith('$2a$'));

const app = express();
const port = process.env.PORT || 3000;

// Configuração do middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Pool de conexão com o MySQL
const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: parseInt(process.env.DB_PORT) || 3306,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASS,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

// Testar conexão inicial com o banco de dados
(async () => {
  try {
    const connection = await pool.getConnection();
    console.log('✅ Conexão com o banco de dados MySQL estabelecida com sucesso.');
    connection.release();
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
    const [rows] = await pool.query('SELECT jogador, avatar, email, senha FROM jogadores');
    // Normalizar retorno para o formato esperado pelo PWA
    const players = rows.map(r => ({
      jogador: r.jogador ? r.jogador.trim() : '',
      avatar: r.avatar || '',
      email: r.email ? r.email.trim() : '',
      senha: '' // nunca expor hash
    }));
    res.json(players);
  } catch (error) {
    console.error('Erro ao buscar jogadores:', error.message);
    res.status(500).json({ error: 'Erro ao buscar jogadores' });
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
            partidas_mes: 0,
            pontos_mes: 0,
            partidas_ano: 0,
            pontos_ano: 0
          };
        }
        const s = playersStats[jogador];

        if (mParts.year === todayParts.year) {
          s.partidas_ano++;
          if (winners.includes(jogador)) {
            s.pontos_ano += pontos;
          }
          if (mParts.month === todayParts.month) {
            s.partidas_mes++;
            if (winners.includes(jogador)) {
              s.pontos_mes += pontos;
            }
            if (mParts.day === todayParts.day) {
              s.partidas_dia++;
              if (winners.includes(jogador)) {
                s.pontos_dia += pontos;
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

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok', time: new Date() });
});

// Versão atual do app — consultado pelo cliente para atualizações automáticas
app.get('/webhook/checar-atualizacao', (req, res) => {
  res.json({
    version_code: 80,
    version_name: "1.45",
    apk_url: "https://github.com/marciobarruda/ClubeDoDomino/releases/download/v80/clube_v_80.apk",
    release_notes: "* v80: Filtro de jogador na aba Buchos mantido após confirmar pagamento. PWA atualizado com novos avatares.",
    min_version: 78
  });
});

// Inicialização do servidor
app.listen(port, () => {
  console.log(`🚀 Servidor rodando na porta ${port}`);
});
