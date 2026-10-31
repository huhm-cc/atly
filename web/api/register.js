import { db, USER_RE, hashPass, newId, body, send } from "../lib/db.js";
export default async function handler(req, res) {
  if (req.method !== "POST") return send(res, 405, { error: "method" });
  const { username = "", password = "" } = await body(req);
  const u = String(username).toLowerCase().trim();
  if (!USER_RE.test(u)) return send(res, 400, { error: "使用者名稱需為 3–20 個英數字、底線或句點" });
  if (String(password).length < 6) return send(res, 400, { error: "密碼至少 6 個字元" });
  const c = await db();
  const salt = newId(8);
  try {
    await c.execute({ sql: "INSERT INTO users (username, pass_hash, salt, created_at) VALUES (?,?,?,?)", args: [u, hashPass(password, salt), salt, Date.now()] });
  } catch { return send(res, 409, { error: "這個名稱已經有人用了" }); }
  const token = newId(24);
  await c.execute({ sql: "INSERT INTO sessions VALUES (?,?,?)", args: [token, u, Date.now()] });
  send(res, 200, { token, username: u });
}
