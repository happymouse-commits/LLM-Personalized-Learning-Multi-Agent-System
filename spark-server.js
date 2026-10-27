const crypto = require('crypto');
const https = require('https');
const http = require('http');

const API_KEY = process.env.SPARK_API_KEY || '';
const API_SECRET = process.env.SPARK_API_SECRET || '';
const APP_ID = process.env.SPARK_APP_ID || '';
const HOST = 'emb-cn-huabei-1.xf-yun.com';
const PORT = 9876;

function makeEmbedRequest(text) {
    return new Promise((resolve, reject) => {
        const date = new Date().toUTCString();
        const signStr = `host: ${HOST}\ndate: ${date}\nPOST / HTTP/1.1`;
        const sig = crypto.createHmac('sha256', API_SECRET).update(signStr).digest('base64');
        const auth = `api_key="${API_KEY}",algorithm="hmac-sha256",headers="host date request-line",signature="${sig}"`;
        const b64 = Buffer.from(JSON.stringify([{content: text, role: 'user'}])).toString('base64');
        const body = JSON.stringify({
            header: { app_id: APP_ID, uid: 'node_' + Date.now(), status: 3 },
            parameter: { emb: { domain: 'query', feature: { encoding: 'utf8', compress: 'raw', format: 'plain' } } },
            payload: { messages: { encoding: 'utf8', compress: 'raw', format: 'json', status: 3, text: b64 } }
        });

        const req = https.request({
            hostname: HOST, port: 443, path: '/', method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Host': HOST, 'Date': date, 'Authorization': auth, 'Content-Length': Buffer.byteLength(body) }
        }, res => {
            let d = '';
            res.on('data', c => d += c);
            res.on('end', () => resolve(d));
        });
        req.on('error', e => reject(e));
        req.write(body);
        req.end();
    });
}

function makeBatchEmbedRequest(texts) {
    return new Promise((resolve, reject) => {
        const date = new Date().toUTCString();
        const signStr = `host: ${HOST}\ndate: ${date}\nPOST / HTTP/1.1`;
        const sig = crypto.createHmac('sha256', API_SECRET).update(signStr).digest('base64');
        const auth = `api_key="${API_KEY}",algorithm="hmac-sha256",headers="host date request-line",signature="${sig}"`;
        const b64 = Buffer.from(JSON.stringify([{content: texts.join('\n---\n'), role: 'user'}])).toString('base64');
        const body = JSON.stringify({
            header: { app_id: APP_ID, uid: 'node_batch_' + Date.now(), status: 3 },
            parameter: { emb: { domain: 'query', feature: { encoding: 'utf8', compress: 'raw', format: 'plain' } } },
            payload: { messages: { encoding: 'utf8', compress: 'raw', format: 'json', status: 3, text: b64 } }
        });

        const req = https.request({
            hostname: HOST, port: 443, path: '/', method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Host': HOST, 'Date': date, 'Authorization': auth, 'Content-Length': Buffer.byteLength(body) }
        }, res => {
            let d = '';
            res.on('data', c => d += c);
            res.on('end', () => resolve(d));
        });
        req.on('error', e => reject(e));
        req.write(body);
        req.end();
    });
}

// 内存缓存
const cache = new Map();

const server = http.createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.statusCode = 204;
        res.end();
        return;
    }

    if (req.url === '/health') {
        res.end(JSON.stringify({ status: 'ok', appId: APP_ID }));
        return;
    }

    if (req.method === 'POST' && req.url === '/embed') {
        let body = '';
        req.on('data', c => body += c);
        req.on('end', async () => {
            try {
                const { text } = JSON.parse(body);
                if (!text) { res.end(JSON.stringify({ error: 'no text' })); return; }

                const cacheKey = crypto.createHash('sha256').update(text).digest('hex').substring(0, 32);
                const cached = cache.get(cacheKey);
                if (cached) {
                    if (Date.now() - cached.ts < 3600000) {
                        res.end(JSON.stringify({ vector: cached.vector }));
                        return;
                    } else {
                        cache.delete(cacheKey);
                    }
                }

                const resp = await makeEmbedRequest(text);
                const json = JSON.parse(resp);
                if (json.header && json.header.code === 0 && json.payload && json.payload.feature) {
                    const vector = json.payload.feature.text;
                    if (cache.size >= 500) {
                        const oldestKey = cache.keys().next().value;
                        cache.delete(oldestKey);
                    }
                    cache.set(cacheKey, { vector, ts: Date.now() });
                    res.end(JSON.stringify({ vector }));
                } else {
                    res.statusCode = 500;
                    res.end(JSON.stringify({ error: resp }));
                }
            } catch(e) {
                res.statusCode = 500;
                res.end(JSON.stringify({ error: e.message }));
            }
        });
        return;
    }

    res.statusCode = 404;
    res.end(JSON.stringify({ error: 'not found' }));
});

server.listen(PORT, () => {
    console.log(`Spark Embedding 服务启动: http://localhost:${PORT}`);
    console.log(`appId=${APP_ID}, cache=ready`);
});
