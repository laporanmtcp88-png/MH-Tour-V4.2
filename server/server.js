import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import crypto from 'crypto';
import ipaddr from 'ipaddr.js';
import { AccessToken } from 'livekit-server-sdk';

const app = express();
app.disable('x-powered-by');
app.use(cors());
app.use(express.json({ limit: '16kb' }));

const ALLOWED_SUBNET = process.env.ALLOWED_SUBNET || '192.168.43.0/24';
function allowedLan(req) {
  const raw = String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim().replace(/^::ffff:/, '');
  try {
    const addr = ipaddr.parse(raw);
    const [net, prefix] = ALLOWED_SUBNET.split('/');
    return addr.kind() === ipaddr.parse(net).kind() && addr.match(ipaddr.parse(net), Number(prefix));
  } catch { return false; }
}
app.use((req, res, next) => {
  if (req.path === '/health' || allowedLan(req)) return next();
  return res.status(403).json({ error: 'Akses hanya dari jaringan hotspot Wi-Fi lokal' });
});

const rooms = new Map();
const CODE_RE = /^UM[0-9]{6}$/;

function cleanName(value, fallback) {
  const v = String(value ?? '').trim().slice(0, 80);
  return v || fallback;
}

function roomForCode(code) {
  return rooms.get(String(code || '').trim().toUpperCase()) || null;
}

app.get('/health', (req, res) => {
  res.json({ ok: true, service: 'mh-tour-token-server', version: '3.0-hotspot-wifi-only' });
});

app.post('/session', (req, res) => {
  try {
    const guideName = cleanName(req.body?.guideName, 'Guide');

    let code;
    do {
      code = `UM${crypto.randomInt(100000, 1000000)}`;
    } while (rooms.has(code));

    const roomName = `mh-tour-${crypto.randomUUID()}`;
    rooms.set(code, {
      roomName,
      guideName,
      createdAt: Date.now()
    });

    res.status(201).json({ code, roomName, guideName });
  } catch (e) {
    res.status(500).json({ error: 'Gagal membuat sesi' });
  }
});

app.post('/token', async (req, res) => {
  try {
    const code = String(req.body?.code || '').trim().toUpperCase();
    const participantName = cleanName(req.body?.participantName, 'Jemaah');
    const role = req.body?.role === 'guide' ? 'guide' : 'jamaah';

    if (!CODE_RE.test(code)) {
      return res.status(400).json({ error: 'Kode sesi tidak valid' });
    }

    const session = roomForCode(code);
    if (!session) {
      return res.status(404).json({ error: 'Sesi tidak ditemukan atau sudah tidak aktif' });
    }

    if (!process.env.LIVEKIT_API_KEY || !process.env.LIVEKIT_API_SECRET || !process.env.LIVEKIT_URL) {
      return res.status(500).json({ error: 'Konfigurasi LiveKit belum lengkap' });
    }

    const identity = crypto.randomUUID();

    const at = new AccessToken(
      process.env.LIVEKIT_API_KEY,
      process.env.LIVEKIT_API_SECRET,
      {
        identity,
        name: participantName,
        ttl: '2h'
      }
    );

    at.addGrant({
      roomJoin: true,
      room: session.roomName,
      canPublish: role === 'guide',
      canSubscribe: true,
      canPublishData: false
    });

    const participantToken = await at.toJwt();

    res.status(201).json({
      serverUrl: process.env.LIVEKIT_URL,
      participantToken,
      roomName: session.roomName,
      identity,
      role
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Gagal membuat token' });
  }
});

const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`MH Tour token server listening on :${port}`));
