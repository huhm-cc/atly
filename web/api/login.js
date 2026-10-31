import { db, hashPass, newId, body, send } from "../lib/db.js";
import crypto from "node:crypto";
export default async function handler(req, res) {
  if (req.method !== "POST") return send(res, 405, { error: "method" });
  const { username = "", password = "" } = await body(req);
  const u = String(username).toLowerCase().trim();
  const c = await db();
  const r = await c.execute({ sql: "SELECT pass_hash, salt FROM users WHERE username = ?", args: [u] });
  const row = r.rows[0];
  if (!row || !crypto.timingSafeEqual(Buffer.from(hashPass(String(password), row.salt), "hex"), Buffer.from(row.pass_hash, "hex")))
    return send(res, 401, { error: "帳號或密碼錯誤" });
  const token = newId(24);
  await c.execute({ sql: "INSERT INTO sessions VALUES (?,?,?)", args: [token, u, Date.now()] });
  send(res, 200, { token, username: u });
}
