import { createClient } from '@supabase/supabase-js'
import { AccessToken } from 'livekit-server-sdk'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Content-Type': 'application/json',
}

type RequestBody = {
  outlet_id?: unknown
  mode?: unknown
  action?: unknown
  error_message?: unknown
}

function json(data: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders })
}

function normalizeOutletIds(rows: unknown): string[] {
  if (!Array.isArray(rows)) return []
  return rows
    .map((row) => typeof row === 'string' ? row : (row as { accessible_outlet_ids?: unknown })?.accessible_outlet_ids)
    .filter((id): id is string => typeof id === 'string' && id.length > 0)
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  if (req.method !== 'POST') return json({ error: 'Method not allowed' }, 405)

  try {
    const authorization = req.headers.get('Authorization')
    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY')
    const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
    const livekitUrl = Deno.env.get('LIVEKIT_URL')
    const livekitApiKey = Deno.env.get('LIVEKIT_API_KEY')
    const livekitApiSecret = Deno.env.get('LIVEKIT_API_SECRET')
    if (!authorization || !supabaseUrl || !anonKey || !serviceKey || !livekitUrl || !livekitApiKey || !livekitApiSecret) {
      return json({ error: 'Server camera monitoring belum dikonfigurasi' }, 503)
    }

    let body: RequestBody
    try {
      body = await req.json()
    } catch {
      return json({ error: 'Request body tidak valid' }, 400)
    }
    const outletId = typeof body.outlet_id === 'string' ? body.outlet_id : ''
    const mode = body.mode === 'viewer' ? 'viewer' : body.mode === 'publisher' ? 'publisher' : ''
    const action = typeof body.action === 'string' ? body.action : 'start'
    if (!outletId || !mode || !['start', 'heartbeat', 'error'].includes(action)) {
      return json({ error: 'outlet_id, mode, atau action tidak valid' }, 400)
    }

    const caller = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authorization } } })
    const { data: { user }, error: userError } = await caller.auth.getUser()
    if (userError || !user) return json({ error: 'Tidak terautentikasi' }, 401)

    const { data: staff, error: staffError } = await caller
      .from('outlet_staff')
      .select('id, name, role, outlet_id, status, is_active')
      .eq('id', user.id)
      .maybeSingle()
    if (staffError || !staff || staff.status !== 'active' || staff.is_active === false) {
      return json({ error: 'Akun staf tidak aktif' }, 403)
    }

    const { data: allowedRows, error: accessError } = await caller.rpc('accessible_outlet_ids')
    if (accessError || !normalizeOutletIds(allowedRows).includes(outletId)) {
      return json({ error: 'Outlet di luar scope akses' }, 403)
    }

    if (mode === 'viewer' && !['admin', 'owner'].includes(staff.role)) {
      return json({ error: 'Hanya Admin dan Owner yang boleh melihat kamera' }, 403)
    }

    const service = createClient(supabaseUrl, serviceKey)
    const roomName = `pos-camera-${outletId}`
    if (mode === 'publisher') {
      const status = action === 'error' ? 'error' : 'live'
      const errorMessage = typeof body.error_message === 'string' ? body.error_message.slice(0, 300) : null
      const { error: sessionError } = await service.from('camera_sessions').upsert({
        outlet_id: outletId,
        staff_id: user.id,
        room_name: roomName,
        status,
        started_at: action === 'start' ? new Date().toISOString() : undefined,
        last_heartbeat_at: new Date().toISOString(),
        error_message: errorMessage,
      }, { onConflict: 'outlet_id' })
      if (sessionError) return json({ error: 'Gagal memperbarui status kamera' }, 500)
    }

    const identity = mode === 'publisher' ? `pos-${outletId}` : `monitor-${user.id}-${crypto.randomUUID()}`
    const token = new AccessToken(livekitApiKey, livekitApiSecret, {
      identity,
      name: mode === 'publisher' ? `POS ${outletId}` : staff.name,
      ttl: 10 * 60,
      attributes: { outlet_id: outletId, role: mode },
    })
    token.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: mode === 'publisher',
      canPublishData: false,
      canSubscribe: mode === 'viewer',
    })

    return json({
      server_url: livekitUrl,
      participant_token: await token.toJwt(),
      room_name: roomName,
      expires_in: 600,
    })
  } catch (error) {
    console.error('livekit-token failed', error)
    return json({ error: 'Gagal membuat sesi live camera' }, 500)
  }
})
