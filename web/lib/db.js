import { createClient } from "@libsql/client/web";
import crypto from "node:crypto";

let client;
let ready;
export function db() {
  if (!client) {
    client = createClient({ url: process.env.TURSO_DATABASE_URL, authToken: process.env.TURSO_AUTH_TOKEN });
  }
  if (!ready) {
    ready = client.batch([
      `CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, pass_hash TEXT NOT NULL, salt TEXT NOT NULL, prompt TEXT, created_at INTEGER NOT NULL)`,
      `CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, username TEXT NOT NULL, created_at INTEGER NOT NULL)`,
      `CREATE TABLE IF NOT EXISTS questions (id TEXT PRIMARY KEY, username TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL, answer TEXT, answered_at INTEGER, ip_hash TEXT)`,
      `CREATE INDEX IF NOT EXISTS q_user ON questions(username, created_at)`,
      `CREATE INDEX IF NOT EXISTS q_ip ON questions(ip_hash, created_at)`
    ], "write").catch(e => { ready = null; throw e; });
  }
  return ready.then(() => client);
}

export const USER_RE = /^[a-z0-9_.]{3,20}$/;
export function hashPass(pw, salt) { return crypto.scryptSync(pw, salt, 64).toString("hex"); }
export function newId(n = 16) { return crypto.randomBytes(n).toString("hex"); }
export function ipHash(req) {
  const ip = (req.headers["x-forwarded-for"] || "").split(",")[0].trim() || "0";
  return crypto.createHash("sha256").update(ip + (process.env.IP_SALT || "atly")).digest("hex").slice(0, 32);
}
export async function body(req) {
  if (req.body && typeof req.body === "object") return req.body;
  try { return JSON.parse(req.body || "{}"); } catch { return {}; }
}
export async function auth(req) {
  const t = (req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (!t) return null;
  const c = await db();
  const r = await c.execute({ sql: "SELECT username FROM sessions WHERE token = ?", args: [t] });
  return r.rows[0]?.username || null;
}
export function send(res, code, data) { res.status(code).setHeader("Cache-Control", "no-store"); res.json(data); }
