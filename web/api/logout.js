import { db, send } from "../lib/db.js";
export default async function handler(req, res) {
  const t = (req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (t) { const c = await db(); await c.execute({ sql: "DELETE FROM sessions WHERE token = ?", args: [t] }); }
  send(res, 200, { ok: true });
}
