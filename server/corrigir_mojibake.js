// Corrige dados com dupla codificação UTF-8 (mojibake) nas tabelas buchos, partidas e
// jogadores. Ex: "LÚIÇA" foi gravado, em algum ponto, como os bytes UTF-8 de "LÚIÇA"
// reinterpretados como Latin-1 e regravados em UTF-8 por cima — o banco então guarda um texto
// que, quando exibido, aparece como "LÃ\x83Ã\x9aIÃ\x83Ã\x87A" / "LÃÍÇA" / variantes.
//
// A correção via SQL puro (CONVERT ... USING latin1/utf8mb4) não funciona de forma confiável
// porque o MySQL substitui por "?" qualquer caractere do UTF-8 original que não exista em
// Latin-1, perdendo informação de forma irrecuperável nesse caminho. Este script faz a correção
// em JavaScript (Buffer), que preserva os bytes exatamente, e só então gera os UPDATEs.
//
// Uso:
//   node corrigir_mojibake.js            -> modo DRY-RUN (só mostra o que mudaria, não grava nada)
//   node corrigir_mojibake.js --aplicar  -> aplica de fato os UPDATEs no banco
//
// Variáveis de ambiente (mesmas do server.js): DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS

require('dotenv').config();
const mysql = require('mysql2/promise');

const APLICAR = process.argv.includes('--aplicar');

// Reverte uma dupla codificação UTF-8: pega os bytes atuais (que o driver já decodificou como
// UTF-8 ao ler do banco, virando uma string JS), reinterpreta cada "caractere" dessa string como
// um byte Latin-1 (reconstituindo os bytes originais pré-regravação), e decodifica esses bytes
// como UTF-8 de novo — recuperando o texto original.
function corrigirDuplaCodificacao(texto) {
  if (!texto) return texto;
  return Buffer.from(texto, 'latin1').toString('utf8');
}

// true se o texto tem a assinatura de dupla codificação: um PAR de caracteres Unicode onde o
// primeiro é Â (U+00C2) ou Ã (U+00C3) e o segundo está no intervalo U+0080-U+00BF. Esse par é
// exatamente o que sobra quando os dois bytes UTF-8 de uma letra acentuada (ex: Á = 0xC3 0x81)
// são separadamente reinterpretados como dois caracteres Latin-1 e then regravados em UTF-8.
//
// IMPORTANTE: o teste é sobre CARACTERES (code points) da string já decodificada pelo driver,
// não sobre os bytes UTF-8 brutos. "Ã" sozinho (ex: em "NÃO", seguido de "O" = 0x4F) é um
// caractere legítimo em português e NÃO deve disparar esse detector — só dispara quando "Ã"/"Â"
// é seguido por outro caractere do bloco de controle C1 (U+0080-U+00BF), que nunca aparece
// isoladamente em texto real e é a marca registrada do bug.
function pareceDuploCodificado(texto) {
  if (!texto) return false;
  for (let i = 0; i < texto.length - 1; i++) {
    const c1 = texto.codePointAt(i);
    const c2 = texto.codePointAt(i + 1);
    if ((c1 === 0xC2 || c1 === 0xC3) && c2 >= 0x80 && c2 <= 0xBF) return true;
  }
  return false;
}

const TABELAS = [
  { nome: 'buchos', pk: 'id_tabela', colunas: ['jogador', 'dupla_vencedora', 'dupla_perdedora', 'obs'] },
  { nome: 'partidas', pk: 'id_tabela', colunas: ['jogador1', 'jogador2', 'jogador3', 'jogador4', 'dupla_vencedora', 'dupla_perdedora', 'cadastrador'] },
  { nome: 'jogadores', pk: 'id_tabela', colunas: ['jogador'] }
];

(async () => {
  const pool = mysql.createPool({
    host: process.env.DB_HOST,
    port: parseInt(process.env.DB_PORT) || 3306,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASS
  });

  console.log(APLICAR ? '⚠️  MODO APLICAR — os UPDATEs serão executados de verdade.' : 'ℹ️  Modo dry-run — nada será gravado. Rode com --aplicar para gravar.');
  console.log('');

  let totalCorrigidas = 0;

  for (const { nome, pk, colunas } of TABELAS) {
    const [rows] = await pool.query(`SELECT ${pk}, ${colunas.join(', ')} FROM ${nome}`);

    for (const row of rows) {
      const updates = {};
      for (const col of colunas) {
        const valorAtual = row[col];
        if (pareceDuploCodificado(valorAtual)) {
          const corrigido = corrigirDuplaCodificacao(valorAtual);
          // Nunca aplica se o resultado contiver o caractere de substituição (U+FFFD) — indica
          // que o byte original já estava perdido antes da dupla codificação (caso irrecuperável
          // por fórmula, ex: "XAM�" em vez de "XAMÃ"), e a "correção" só pioraria o dado.
          if (corrigido.includes('�')) {
            console.log(`[${nome} #${row[pk]}] AVISO: ${col} tem caractere irrecuperável, pulando: "${valorAtual}"`);
            continue;
          }
          updates[col] = corrigido;
        }
      }

      if (Object.keys(updates).length > 0) {
        totalCorrigidas++;
        const descricao = Object.entries(updates)
          .map(([col, novo]) => `${col}: "${row[col]}" -> "${novo}"`)
          .join(' | ');
        console.log(`[${nome} #${row[pk]}] ${descricao}`);

        if (APLICAR) {
          const setClause = Object.keys(updates).map(col => `${col} = ?`).join(', ');
          const values = [...Object.values(updates), row[pk]];
          await pool.query(`UPDATE ${nome} SET ${setClause} WHERE ${pk} = ?`, values);
        }
      }
    }
  }

  console.log('');
  console.log(`Total de linhas ${APLICAR ? 'corrigidas' : 'que seriam corrigidas'}: ${totalCorrigidas}`);
  await pool.end();
})().catch(err => {
  console.error('Erro:', err.message);
  process.exit(1);
});
