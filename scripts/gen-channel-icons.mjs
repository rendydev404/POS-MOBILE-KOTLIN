// Membuat vector drawable logo channel food-app dari sumber kebenaran POS web
// (apps/pos-kasir/lib/channels.ts). Dijalankan ulang kalau brand mark di web
// berubah, supaya native tidak pernah kembali menggambar logo dengan tangan.
//
//   node scripts/gen-channel-icons.mjs [path/ke/channels.ts]
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const DEFAULT_SRC =
  'C:/Users/Creator MPB/OneDrive/Desktop/New folder/DIGITALISASI-SS-PROJECT/apps/pos-kasir/lib/channels.ts'

const src = readFileSync(process.argv[2] ?? DEFAULT_SRC, 'utf8')
const outDir = resolve(dirname(fileURLToPath(import.meta.url)), '../app/src/main/res/drawable')

const entryRe =
  /\{\s*id:\s*'([^']+)',\s*label:\s*'([^']+)',\s*bg:\s*'([^']+)',\s*fg:\s*'([^']+)',\s*mark:\s*'([^']*)'(?:,\s*logoPath:\s*'([^']+)')?\s*\}/g

// Web membungkus glyph 16px di dalam lingkaran 28px (order-manual/page.tsx).
// Rasio yang sama dipertahankan di viewport 24 milik vector drawable.
const SCALE = 16 / 28
const OFFSET = (24 - 24 * SCALE) / 2

const FILES = {
  gofood: 'ic_gofood.xml',
  shopeefood: 'ic_shopeefood.xml',
  grabfood: 'ic_grabfood.xml',
  tiktokgo: 'ic_tiktokgo.xml',
}

let written = 0
for (const [, id, label, bg, fg, , logoPath] of src.matchAll(entryRe)) {
  const file = FILES[id]
  if (!file || !logoPath) continue

  // `<`, `>`, `&` akan merusak atribut XML — kalau brand mark baru mengandungnya,
  // lebih baik gagal keras daripada menulis drawable yang tak bisa di-parse aapt.
  if (/[<>&"]/.test(logoPath)) {
    console.error(`${id}: logoPath mengandung karakter tak aman untuk atribut XML`)
    process.exit(1)
  }

  writeFileSync(
    `${outDir}/${file}`,
    `<!-- ${label}. Dibuat oleh scripts/gen-channel-icons.mjs dari
     apps/pos-kasir/lib/channels.ts (POS web) — jangan digambar ulang tangan.
     Lingkaran brand ${bg} + glyph ${fg}, proporsi sama dengan chip web (16/28). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="${bg}"
        android:pathData="M12,0 A12,12 0 1,1 12,24 A12,12 0 1,1 12,0 Z" />
    <group
        android:scaleX="${SCALE.toFixed(4)}"
        android:scaleY="${SCALE.toFixed(4)}"
        android:translateX="${OFFSET.toFixed(4)}"
        android:translateY="${OFFSET.toFixed(4)}">
        <path
            android:fillColor="${fg}"
            android:pathData="${logoPath}" />
    </group>
</vector>
`,
    'utf8'
  )
  console.log(`${file} <- ${label} (${bg})`)
  written++
}

if (written !== Object.keys(FILES).length) {
  console.error(`hanya ${written}/${Object.keys(FILES).length} channel ketemu di channels.ts`)
  process.exit(1)
}
