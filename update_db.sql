UPDATE global_settings SET value = '{
  "version_code": 55,
  "version_name": "1.0.54",
  "apk_url": "https://khpkoreaaucvyqfhynfq.supabase.co/storage/v1/object/public/app-releases/suka-shawarma-1.0.54.apk",
  "apk_sha256": "67de78594905d18645ca63d1cd6e3a136c0aeba4748bd8976861aeb38ec8d114",
  "apk_size_bytes": 14132171,
  "mandatory": false,
  "deltas": [
    {
      "base_version_code": 54,
      "patch_url": "https://khpkoreaaucvyqfhynfq.supabase.co/storage/v1/object/public/app-releases/suka-shawarma-1.0.53-to-1.0.54.fbf",
      "patch_sha256": "6c32d75276f1bd77321062edff4f382c37093c80070ee7569019cb51608a38e4",
      "patch_size_bytes": 447666
    }
  ]
}'::jsonb WHERE key = 'app_update';
