module.exports = async function handler(req, res) {
  const configuredUrl = process.env.RENDER_API_URL;
  if (!configuredUrl) {
    return res.status(500).json({ error: 'The RENDER_API_URL environment variable is not configured.' });
  }

  let upstream;
  try {
    upstream = new URL(configuredUrl);
  } catch (error) {
    console.error('Invalid RENDER_API_URL:', error.message);
    return res.status(500).json({ error: 'The configured API URL is invalid.' });
  }
  if (upstream.protocol !== 'https:') {
    return res.status(500).json({ error: 'RENDER_API_URL must use HTTPS.' });
  }

  const incoming = new URL(req.url, 'https://vercel.invalid');
  if (!incoming.pathname.startsWith('/api/')) {
    return res.status(404).json({ error: 'Not found.' });
  }
  upstream.pathname = incoming.pathname;
  upstream.search = incoming.search;

  const headers = {};
  for (const name of ['authorization', 'content-type']) {
    if (req.headers[name]) headers[name] = req.headers[name];
  }

  let body;
  if (req.method !== 'GET' && req.method !== 'HEAD' && req.body !== undefined) {
    body = typeof req.body === 'string' || Buffer.isBuffer(req.body)
      ? req.body
      : JSON.stringify(req.body);
  }

  try {
    const response = await fetch(upstream, { method: req.method, headers, body });
    const responseBody = Buffer.from(await response.arrayBuffer());
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', response.headers.get('content-type') || 'application/json');
    return res.status(response.status).send(responseBody);
  } catch (error) {
    console.error('Render API request failed:', error);
    return res.status(502).json({ error: 'The application API is temporarily unavailable.' });
  }
};
