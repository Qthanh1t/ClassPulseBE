# ClassPulse — Hướng dẫn deploy production (VPS Linux, full Docker, HTTPS)

Kiến trúc: **Caddy** (edge, tự động HTTPS) đứng trước, serve SPA tĩnh và reverse-proxy
`/api` `/ws` `/storage` về backend & MinIO; **LiveKit** và **MinIO S3** mỗi cái 1 subdomain.

```
                         ┌──────────────── VPS (Docker) ────────────────┐
Browser ──HTTPS/WSS──►   │  Caddy :80/:443  ──► SPA (/srv)                │
                         │       ├─ /api/*    ──► backend:8080            │
                         │       ├─ /ws/*     ──► backend:8080 (STOMP)    │
                         │       └─ /storage/*──► minio:9000 (GET công khai)│
   livekit.<dom> ─WSS─►  │  Caddy ──► livekit:7880 (signaling)            │
   media UDP/TCP ─────►  │  livekit :7882/udp  :7881/tcp (đi thẳng IP)    │
   minio.<dom>  ─HTTPS►  │  Caddy ──► minio:9000 (presigned PUT upload)   │
                         │  backend  postgres  redis  minio  livekit      │
                         └───────────────────────────────────────────────┘
```

## 0. Yêu cầu trước khi bắt đầu

- VPS Ubuntu/Debian, ≥ 4GB RAM, có IP public.
- 1 domain. Tạo **3 bản ghi DNS A** trỏ về IP VPS:
  - `classpulse.example.com`  (app)
  - `livekit.example.com`     (LiveKit signaling)
  - `minio.example.com`       (MinIO S3 API cho upload)
- Mở firewall (xem mục 4).

## 1. Cài Docker trên VPS

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER        # đăng xuất/đăng nhập lại để có hiệu lực
docker compose version               # xác nhận có Docker Compose v2
```

## 2. Lấy mã nguồn lên VPS

Hai repo phải nằm cạnh nhau (compose build FE từ `../ClassPulseFE`):

```bash
mkdir -p ~/datn && cd ~/datn
git clone <url-backend>  classpulse
git clone <url-frontend> ClassPulseFE
cd classpulse
```

## 3. Cấu hình biến môi trường

```bash
cp .env.prod.example .env.prod
openssl rand -hex 64        # → dán vào JWT_SECRET
openssl rand -base64 32     # → dùng cho LIVEKIT_API_SECRET (>=32 ký tự)
nano .env.prod
```

Điền: 3 domain, `ACME_EMAIL`, `LIVEKIT_NODE_IP` = **IP public của VPS**, mật khẩu DB/MinIO,
`JWT_SECRET`, `LIVEKIT_API_SECRET`.

**Đồng bộ LiveKit secret** — sửa `livekit.prod.yaml`, thay dòng:
```yaml
keys:
  classpulse: <đúng giá trị LIVEKIT_API_SECRET>
```
(`classpulse` = `LIVEKIT_API_KEY`, giá trị = `LIVEKIT_API_SECRET`.)

## 4. Mở firewall

| Port | Giao thức | Mục đích |
|---|---|---|
| 80, 443 | TCP | HTTP/HTTPS (Caddy + ACME) |
| 443 | UDP | HTTP/3 (tùy chọn) |
| 7881 | TCP | LiveKit ICE/TCP fallback |
| 7882 | UDP | LiveKit media |

```bash
sudo ufw allow 80,443/tcp
sudo ufw allow 443/udp
sudo ufw allow 7881/tcp
sudo ufw allow 7882/udp
sudo ufw enable
```
> KHÔNG mở 5432 (Postgres), 6379 (Redis), 9000/9001 (MinIO), 7880 (LiveKit signaling) ra ngoài
> — chúng chạy nội bộ qua Docker network; MinIO console 9001 chỉ bind 127.0.0.1 (vào qua SSH tunnel).

## 5. Build & chạy

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Lần đầu Caddy tự xin chứng chỉ Let's Encrypt cho cả 3 domain (mất ~30s). Theo dõi log:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f caddy backend
```

## 6. Kiểm tra

- `https://classpulse.example.com` → mở được app, đăng nhập OK (cookie refresh có Secure).
- Upload avatar/tài liệu → PUT tới `https://minio.example.com/...` thành công, ảnh hiển thị qua `/storage/...`.
- Vào 1 phiên học bằng 2 trình duyệt → video LiveKit (qua `wss://livekit.example.com`) hiện hình hai chiều.
- `https://classpulse.example.com/swagger-ui.html` → API docs (cân nhắc tắt ở prod, xem mục 9).

## 7. Khởi tạo bucket MinIO

Backend tự tạo bucket `classpulse` với policy public-read khi khởi động (xem `MinioConfig`).
Nếu cần thao tác tay, mở console qua SSH tunnel:
```bash
ssh -L 9001:127.0.0.1:9001 user@vps   # rồi mở http://localhost:9001
```

## 8. Vận hành

```bash
# Cập nhật code mới
cd ~/datn/classpulse && git pull
cd ~/datn/ClassPulseFE && git pull
cd ~/datn/classpulse
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# Xem trạng thái / log
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend

# Backup database
docker exec classpulse-postgres pg_dump -U classpulse classpulse > backup_$(date +%F).sql

# Dừng
docker compose -f docker-compose.prod.yml --env-file .env.prod down
```

## 9. Cứng hóa production (khuyến nghị)

- Đổi toàn bộ secret mặc định (đã làm ở mục 3).
- Tắt Swagger: đặt `springdoc.swagger-ui.enabled=false` (qua env `SPRINGDOC_SWAGGER_UI_ENABLED=false`).
- Bật backup định kỳ Postgres + volume `minio_data`.
- Cân nhắc giới hạn tài nguyên (`deploy.resources`) cho backend/livekit.
- Theo dõi `https://classpulse.example.com/actuator/health`.

## Sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| Video không lên giữa 2 máy | Thiếu mở UDP 7882 / TCP 7881, hoặc `LIVEKIT_NODE_IP` sai IP public. |
| `wss` LiveKit lỗi | DNS `livekit.<dom>` chưa trỏ đúng, hoặc `LIVEKIT_API_SECRET` ≠ `keys:` trong `livekit.prod.yaml`. |
| Upload PUT 403/SignatureDoesNotMatch | `MINIO_ENDPOINT` không khớp host browser gọi → phải là `https://minio.<dom>`, DNS đã trỏ. |
| Ảnh không hiển thị (`/storage/...` 404) | Caddy chưa proxy `/storage` hoặc bucket chưa public-read. |
| Đăng nhập rồi mất session | Cookie cần HTTPS — đảm bảo `APP_COOKIE_SECURE=true` và truy cập qua `https://`. |
| Caddy không xin được cert | DNS chưa trỏ về VPS, hoặc port 80/443 bị chặn. |
| Backend báo Flyway/`ddl validate` lỗi | DB cũ không khớp migration — kiểm tra version hoặc dùng DB sạch. |
