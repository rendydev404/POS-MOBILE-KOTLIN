// Specifier npm: ditulis langsung supaya file ini bisa di-deploy lewat editor
// dashboard tanpa ikut membawa deno.json.
import { createClient } from 'npm:@supabase/supabase-js@2'
import { SignJWT, importPKCS8 } from 'npm:jose@5'

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
function buildDataPayload(type: string, record: Record<string, any>): Record<string, string> | null {
  if (type === 'owner_message') {
    return {
      type,
      id: String(record.id ?? ''),
      title: String(record.title ?? 'Pesan dari Owner'),
      body: String(record.body ?? 'Ada pesan baru untuk Anda.'),
    }
  }
  
  if (type === 'petty_cash') {
    const amountStr = record.amount ? `Rp ${Number(record.amount).toLocaleString('id-ID')}` : '';
    let title = 'Update Petty Cash';
    let body = `Data petty cash Anda telah diperbarui.`;
    
    if (record.status) {
       if (record.status === 'forwarded_to_area_manager') {
           title = 'Status Petty Cash';
           body = `Menunggu review AM.`;
       } else if (record.status === 'forwarded_to_finance') {
           title = 'Status Petty Cash';
           body = `Sedang menunggu pencairan Finance.`;
       } else if (record.status === 'approved_by_finance' || record.status === 'forwarded_by_finance') {
           title = 'Status Petty Cash';
           body = `Dana ada di AM.`;
       } else if (record.status === 'forwarded_by_area_manager') {
           title = 'Status Petty Cash';
           body = `Dana sudah diserahkan oleh AM ke Leader.`;
       } else if (record.status === 'completed' || record.status === 'forwarded_by_leader' || record.status === 'approved') {
           title = 'Petty Cash Cair';
           body = `Saldo petty cash sudah masuk, Gunakan dengan sebaik-baiknya yaa😇😇`;
       } else if (record.status === 'rejected' || record.status === 'cancelled') {
           title = 'Top Up Dibatalkan';
           body = `Pengajuan top up petty cash ditolak/dibatalkan.`;
       } else if (record.status === 'pending') {
           title = 'Top Up Baru';
           body = `Pengajuan top up petty cash menunggu persetujuan.`;
       } else {
           // Do not send push notification for intermediate or unrecognized states not explicitly handled
           return null
       }
    }
    
    return {
      type,
      id: String(record.id ?? ''),
      title,
      body,
    }
  }

  if (type === 'order_cancelled') {
    return {
      type,
      id: String(record.id ?? ''),
      order_id: String(record.id ?? ''),
      title: 'Pesanan Dibatalkan',
      body: record.order_number
        ? `Order #${record.order_number} dibatalkan.`
        : 'Ada pesanan yang dibatalkan.',
    }
  }

  return {
    type: 'new_order',
    id: String(record.id ?? ''),
    order_id: String(record.id ?? ''),
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

    // Kalau secret-nya rusak, jangan biarkan menyamar sebagai error JSON generik —
    // sebutkan variabel mana yang salah supaya langsung ketahuan.
    let serviceAccount: Record<string, any>
    try {
      serviceAccount = JSON.parse(serviceAccountStr)
    } catch (_e) {
      return new Response(
        JSON.stringify({
          error: 'FIREBASE_SERVICE_ACCOUNT bukan JSON valid. Paste ulang isi utuh file service account dari Firebase Console.',
          preview: serviceAccountStr.slice(0, 40),
        }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const projectId = serviceAccount.project_id
    // Project service account harus sama dengan project google-services.json app
    // (pos-native-de856); kalau beda, FCM balas 403 untuk semua token.
    if (!projectId) {
      return new Response(
        JSON.stringify({ error: 'FIREBASE_SERVICE_ACCOUNT tidak punya project_id' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }
    // Sebagian cara paste menyimpan private_key dengan "\n" literal, bukan baris
    // baru sungguhan — importPKCS8 menolaknya. Normalkan dulu.
    const privateKey = String(serviceAccount.private_key ?? '').replace(/\\n/g, '\n')
    const accessToken = await getValidAccessToken(serviceAccount.client_email, privateKey)

    const dataPayload = buildDataPayload(type, record)
    if (dataPayload === null) {
      return new Response(JSON.stringify({ message: 'No explicit notification for this state' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }

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
        // Hanya UNREGISTERED (404) yang benar-benar berarti token mati.
        // 403 JANGAN dibuang: itu PERMISSION_DENIED di level kredensial/project
        // (service account salah, FCM API belum aktif) yang mengenai SEMUA token
        // sekaligus — memperlakukannya sebagai token mati akan mengosongkan
        // seluruh tabel dalam sekali kirim, dan push mati permanen sesudahnya.
        const errorCode = data?.error?.details?.find((d: any) =>
          d['@type']?.includes('FcmError'))?.errorCode
        if (res.status === 404 || errorCode === 'UNREGISTERED') {
          deadTokens.push(token)
        } else if (res.status === 403) {
          console.error(`FCM 403 untuk token ${token.slice(0, 12)}… — kredensial/project bermasalah, token TIDAK dihapus:`, JSON.stringify(data))
        }
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
