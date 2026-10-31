import { db, auth, body, send } from "../lib/db.js";
export default async function handler(req, res) {
  const me = await auth(req); if (!me) return send(res, 401, { error: "請先登入" });
  const c = await db();
  if (req.method === "GET") {
    const r = await c.execute({ sql: "SELECT id, body, created_at, answer, answered_at FROM questions WHERE username = ? ORDER BY created_at DESC LIMIT 200", args: [me] });
    const p = await c.execute({ sql: "SELECT prompt FROM users WHERE username = ?", args: [me] });
    return send(res, 200, { username: me, prompt: p.rows[0]?.prompt || "問我任何匿名的問題吧！", questions: r.rows.map(x => ({ ...x })) });
  }
  if (req.method === "POST") {
    const { id, answer = "" } = await body(req);
    const a = String(answer).trim().slice(0, 500);
    await c.execute({ sql: "UPDATE questions SET answer = ?, answered_at = ? WHERE id = ? AND username = ?", args: [a || null, a ? Date.now() : null, id, me] });
    return send(res, 200, { ok: true });
  }
  if (req.method === "DELETE") {
    const { id } = await body(req);
    await c.execute({ sql: "DELETE FROM questions WHERE id = ? AND username = ?", args: [id, me] });
    return send(res, 200, { ok: true });
  }
  send(res, 405, { error: "method" });
}
