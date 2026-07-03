/**
 * Script de migração one-shot: rehasha todas as senhas em texto puro para bcrypt.
 * Execute UMA VEZ no servidor após o deploy:
 *   node migrate-passwords.js
 *
 * Senhas que já são hash bcrypt ($2b$/$2a$) são puladas automaticamente.
 */
require('dotenv').config();
const mysql = require('mysql2/promise');
const bcrypt = require('bcryptjs');

const BCRYPT_ROUNDS = 10;

async function main() {
  const pool = await mysql.createPool({
    host: process.env.DB_HOST,
    port: parseInt(process.env.DB_PORT) || 3306,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASS,
  });

  const [rows] = await pool.query('SELECT email, senha FROM jogadores');
  console.log(`Total de jogadores: ${rows.length}`);

  let migrated = 0;
  let skipped = 0;

  for (const row of rows) {
    const senha = row.senha ? row.senha.trim() : '';
    if (!senha) { skipped++; continue; }
    if (senha.startsWith('$2b$') || senha.startsWith('$2a$')) { skipped++; continue; }

    const hash = await bcrypt.hash(senha, BCRYPT_ROUNDS);
    await pool.query('UPDATE jogadores SET senha = ? WHERE email = ?', [hash, row.email]);
    console.log(`  Migrado: ${row.email}`);
    migrated++;
  }

  console.log(`\nMigração concluída: ${migrated} migrados, ${skipped} pulados.`);
  await pool.end();
}

main().catch(err => { console.error(err); process.exit(1); });
