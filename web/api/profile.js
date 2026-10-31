import { db, auth, body, send } from "../lib/db.js";
export default async function handler(req, res) {
  const c = await db();
  if (req.method === "GET") {
    const u = String(req.query.u || "").toLowerCase();
    const r = await c.execute({ sql: "SELECT username, prompt FROM users WHERE username = ?", args: [u] });
    if (!r.rows[0]) return send(res, 404, { error: "找不到這個使用者" });
    return send(res, 200, { username: r.rows[0].username, prompt: r.rows[0].prompt || "問我任何匿名的問題吧！" });
  }
  if (req.method === "POST") {
    const me = await auth(req); if (!me) return send(res, 401, { error: "請先登入" });
    const { prompt = "" } = await body(req);
    await c.execute({ sql: "UPDATE users SET prompt = ? WHERE username = ?", args: [String(prompt).slice(0, 80), me] });
    return send(res, 200, { ok: true });
  }
  send(res, 405, { error: "method" });
}
