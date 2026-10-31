# atly

匿名問我任何事。每一則，都來自真人。

atly 是一個匿名提問服務，跟 NGL 類似，但**絕不會自動發送假問題**。收到的每一則問題，都是真的有人想問你。

- 網站：https://atly.huhm.cc
- Android：到 [Releases](https://github.com/huhm-cc/atly/releases) 下載最新 APK

## 專案結構

- `web/`：網站與 API（Vercel Serverless Functions + Turso）
  - `public/index.html`：前端（首頁、提問頁 `/@username`、收件匣 `/inbox`）
  - `api/`：註冊、登入、提問、收件匣、回答
  - `lib/db.js`：Turso 連線與資料表
- `android/`：Android App（WebView 包裝 + 一鍵發布到 IG 限動）
- `.github/workflows/android.yml`：自動建置 APK 並發布到 Releases

## 環境變數（web）

在 Vercel 設定：`TURSO_DATABASE_URL`、`TURSO_AUTH_TOKEN`、`IP_SALT`。

## Android 自動更新

- 手動開啟 App 時檢查 GitHub Releases，有新版會顯示「更新中」、把資料存進 `fetch-data.json`、下載並開啟安裝。
- 新版啟動時若找到 `fetch-data.json`，顯示「載入資料中，請稍後」，還原後立即刪除該檔並顯示更新內容。
- Release 需要固定簽章：在 repo Secrets 設定 `ATLY_KEYSTORE_B64`（PKCS12 keystore 的 base64）。
