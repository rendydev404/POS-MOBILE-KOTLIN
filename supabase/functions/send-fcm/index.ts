import { createClient } from '@supabase/supabase-js'
import { initializeApp, cert, getApps, getApp } from 'firebase-admin/app'
import { getMessaging } from 'firebase-admin/messaging'

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

    // Initialize Firebase Admin (only once per instance)
    const serviceAccountStr = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
    if (!serviceAccountStr) {
      return new Response(
        JSON.stringify({ error: 'Firebase service account not configured' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const serviceAccount = JSON.parse(serviceAccountStr)
    const app = getApps().length === 0 ? initializeApp({
      credential: cert(serviceAccount)
    }) : getApp()

    const messaging = getMessaging(app)

    const title = record.title || 'New Update'
    const body = record.body || 'You have a new notification.'

    const message = {
      notification: {
        title: title,
        body: body,
      },
      data: {
        recordId: String(record.id || ''),
        action: 'record_updated'
      },
      tokens: tokenStrings
    }

    const response = await messaging.sendMulticast(message)
    console.log('Successfully sent messages:', response.successCount)

    return new Response(
      JSON.stringify({ success: true, successCount: response.successCount }),
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
