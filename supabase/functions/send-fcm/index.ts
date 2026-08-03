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

Deno.serve(async (req) => {
  try {
    const payload = await req.json()
    const record = payload.record

    if (!record || !record.outlet_id) {
      return new Response(
        JSON.stringify({ error: 'No outlet_id found in record' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const outletId = record.outlet_id

    // Initialize Supabase Client
    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')

    if (!supabaseUrl || !supabaseAnonKey) {
      return new Response(
        JSON.stringify({ error: 'Supabase credentials not configured' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const supabase = createClient(supabaseUrl, supabaseAnonKey)

    // Fetch tokens
    const { data: tokens, error } = await supabase
      .from('fcm_tokens')
      .select('token')
      .eq('outlet_id', outletId)

    if (error) {
      console.error('Error fetching tokens:', error)
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    if (!tokens || tokens.length === 0) {
      return new Response(
        JSON.stringify({ message: 'No tokens found for outlet' }),
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
    
    // Ensure title, body, and type exist in the data payload for the Android app
    const dataPayload: Record<string, string> = {
      title: String(record.title || 'System Notification'),
      body: String(record.body || 'You have a new update.'),
      type: String(record.type || 'DEFAULT'),
    }

    // Merge in all other fields from record, ensuring they are strings
    for (const [k, v] of Object.entries(record)) {
      if (v !== null && v !== undefined) {
        dataPayload[k] = String(v)
      }
    }

    const results = []
    const CHUNK_SIZE = 25

    // Send requests to Firebase HTTP v1 API in chunks
    for (let i = 0; i < tokenStrings.length; i += CHUNK_SIZE) {
      const chunk = tokenStrings.slice(i, i + CHUNK_SIZE)
      const chunkPromises = chunk.map(async (token: string) => {
        const messagePayload = {
          message: {
            token,
            data: dataPayload
          }
        }

        const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(messagePayload)
        })
        
        const data = await res.json()
        return { token, status: res.status, data }
      })
      
      const chunkResults = await Promise.all(chunkPromises)
      results.push(...chunkResults)
    }
    
    return new Response(
      JSON.stringify({ success: true, results }),
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
