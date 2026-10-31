import { db, ipHash, newId, body, send } from "../lib/db.js";
export default async function handler(req, res) {
  if (req.method !== "POST") return send(res, 405, { error: "method" });
  const { username = "", text = "" } = await body(req);
  const u = String(username).toLowerCase();
  const t = String(text).trim();
  if (!t || t.length > 300) return send(res, 400, { error: "問題需為 1–300 字" });
  const c = await db();
  const exists = await c.execute({ sql: "SELECT 1 FROM users WHERE username = ?", args: [u] });
  if (!exists.rows[0]) return send(res, 404, { error: "找不到這個使用者" });
  const ih = ipHash(req);
  const recent = await c.execute({ sql: "SELECT COUNT(*) AS n FROM questions WHERE ip_hash = ? AND created_at > ?", args: [ih, Date.now() - 60000] });
  if (Number(recent.rows[0].n) >= 5) return send(res, 429, { error: "傳太快了，等一分鐘再試" });
  await c.execute({ sql: "INSERT INTO questions (id, username, body, created_at, ip_hash) VALUES (?,?,?,?,?)", args: [newId(10), u, t, Date.now(), ih] });
  send(res, 200, { ok: true });
}
