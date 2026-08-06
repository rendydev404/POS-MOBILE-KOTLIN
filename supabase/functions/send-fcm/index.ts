import { createClient } from '@supabase/supabase-js'
import { SignJWT, importPKCS8 } from 'jose'

let cachedToken: string | null = null
let tokenExpiry: number = 0

async function getValidAccessToken(clientEmail: string, privateKey: string) {
  const now = Date.now()
  // Refresh token if it expires in less than 5 minutes
  if (cachedToken && tokenExpiry > now + 5 * 60 * 1000) {
    return cachedToken
  }

  const privateKeyObj = await importPKCS8(privateKey, 'RS256')
  const jwt = await new SignJWT({
    iss: clientEmail,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
  })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(privateKeyObj)

  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  })

  const data = await response.json()
  if (!response.ok) {
    throw new Error(`Error fetching access token: ${data.error_description || JSON.stringify(data)}`)
  }

  cachedToken = data.access_token
  // expires_in is in seconds
  tokenExpiry = now + (data.expires_in * 1000)
  return cachedToken
}

/** Data-only: app yang membangun notifikasinya sendiri (custom sound + dedup pakai id). */
function buildDataPayload(type: string, record: Record<string, any>): Record<string, string> {
  if (type === 'owner_message') {
    return {
      type,
      id: String(record.id ?? ''),
      title: String(record.title ?? 'Pesan dari Owner'),
      body: String(record.body ?? 'Ada pesan baru untuk Anda.'),
    }
  }
  return {
    type: 'new_order',
    id: String(record.id ?? ''),
    title: 'Pesanan Baru Masuk',
    body: record.order_number
      ? `Order #${record.order_number} menunggu diproses.`
      : 'Ada pesanan baru menunggu diproses.',
  }
}

Deno.serve(async (req) => {
  try {
    const payload = await req.json()
    const record = payload.record ?? {}
    // Trigger mengirim `type` di level body, bukan di dalam record.
    const type = String(payload.type ?? 'new_order')

    // Order yang dibuat kasir sendiri tidak perlu push balik ke kasir itu.
    if (type === 'new_order' && String(record.source ?? '').toLowerCase() === 'pos') {
      return new Response(JSON.stringify({ skipped: 'pos-sourced order' }), { status: 200 })
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    // Service role: perlu untuk baca semua fcm_tokens dan menghapus token mati.
    const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
    if (!supabaseUrl || !serviceKey) {
      return new Response(
        JSON.stringify({ error: 'Supabase credentials not configured' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }
    const supabase = createClient(supabaseUrl, serviceKey)

    // owner_messages tidak punya outlet_id (broadcast ke semua outlet) — jangan difilter.
    let query = supabase.from('fcm_tokens').select('token')
    if (record.outlet_id) query = query.eq('outlet_id', record.outlet_id)
    const { data: tokens, error } = await query

    if (error) {
      console.error('Error fetching tokens:', error)
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    if (!tokens || tokens.length === 0) {
      return new Response(
        JSON.stringify({ message: 'No tokens found' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const tokenStrings = tokens.map((t: any) => t.token)

    const serviceAccountStr = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
    if (!serviceAccountStr) {
      return new Response(
        JSON.stringify({ error: 'Firebase service account not configured' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const serviceAccount = JSON.parse(serviceAccountStr)
    const projectId = serviceAccount.project_id
    const accessToken = await getValidAccessToken(serviceAccount.client_email, serviceAccount.private_key)

    const dataPayload = buildDataPayload(type, record)

    const results = []
    const deadTokens: string[] = []
    const CHUNK_SIZE = 25

    for (let i = 0; i < tokenStrings.length; i += CHUNK_SIZE) {
      const chunk = tokenStrings.slice(i, i + CHUNK_SIZE)
      const chunkPromises = chunk.map(async (token: string) => {
        const messagePayload = {
          message: {
            token,
            data: dataPayload,
            // Data-only butuh HIGH priority agar app tetap dibangunkan saat Doze/background.
            android: { priority: 'HIGH' },
          },
        }

        const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(messagePayload),
        })

        const data = await res.json()
        // UNREGISTERED / SENDER_ID_MISMATCH = token mati, buang agar tabel tidak menumpuk.
        if (res.status === 404 || res.status === 403) deadTokens.push(token)
        return { token, status: res.status, data }
      })

      results.push(...await Promise.all(chunkPromises))
    }

    if (deadTokens.length > 0) {
      await supabase.from('fcm_tokens').delete().in('token', deadTokens)
    }

    return new Response(
      JSON.stringify({ success: true, sent: results.length, pruned: deadTokens.length, results }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )

  } catch (err: any) {
    console.error('Error in edge function:', err)
    return new Response(
      JSON.stringify({ error: err.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
