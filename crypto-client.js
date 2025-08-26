const crypto = require('crypto');
const http = require('http');

const KEY_BASE64 = "ThhJJ7YbMgm7ELo590J2sIO+y3vqEt2ZOVDQuaxqllE=" // same as server
const key = Buffer.from(KEY_BASE64, 'base64'); // 32 bytes

function encryptJson(obj, aad) {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    if (aad) cipher.setAAD(Buffer.from(aad, 'utf8'));

    const plaintext = Buffer.from(JSON.stringify(obj), 'utf8');
    const enc = Buffer.concat([cipher.update(plaintext), cipher.final()]);
    const tag = cipher.getAuthTag();
    const ciphertextPlusTag = Buffer.concat([enc, tag]);

    return {
        iv: iv.toString('base64'),
        ciphertext: ciphertextPlusTag.toString('base64'),
    };
}

function decryptPayload(payload, aad) {
    const iv = Buffer.from(payload.iv, 'base64');
    const data = Buffer.from(payload.ciphertext, 'base64');
    const tag = data.slice(data.length - 16);
    const ciphertext = data.slice(0, data.length - 16);

    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    if (aad) decipher.setAAD(Buffer.from(aad, 'utf8'));
    decipher.setAuthTag(tag);

    const dec = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
    return JSON.parse(dec.toString('utf8'));
}

const aad = 'POST /api/echo';
const body = encryptJson({ message: 'hello', times: 3 }, aad);
const json = JSON.stringify(body);

const req = http.request({
    hostname: 'localhost',
    port: 8080, // if TLS
    path: '/api/echo',
    method: 'POST',
    headers: {
        'Content-Type': 'application/encrypted+json',
        'Accept': 'application/encrypted+json',
        'X-Encrypted': '1',
        'Content-Length': Buffer.byteLength(json),
    },
    rejectUnauthorized: false // for local self-signed dev only
}, (res) => {
    let data = '';
    res.on('data', chunk => data += chunk);
    res.on('end', () => {
        const payload = JSON.parse(data);
        const resp = decryptPayload(payload, 'POST /api/echo');
        console.log('Decrypted response:', resp);
    });
});

req.on('error', console.error);
req.write(json);
req.end();