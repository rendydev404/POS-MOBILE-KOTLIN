import urllib.request

def download(url, filename):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    with urllib.request.urlopen(req) as response:
        with open(filename, 'wb') as out_file:
            out_file.write(response.read())
            print(f"Downloaded {filename}")

download('https://upload.wikimedia.org/wikipedia/commons/thumb/8/86/Gojek_logo.svg/256px-Gojek_logo.svg.png', 'd:\\PROJECT-APPS-NATIVE\\POS\\app\\src\\main\\res\\drawable\\ic_gofood.png')
download('https://upload.wikimedia.org/wikipedia/commons/thumb/0/03/Grab_Logo.svg/256px-Grab_Logo.svg.png', 'd:\\PROJECT-APPS-NATIVE\\POS\\app\\src\\main\\res\\drawable\\ic_grabfood.png')
download('https://upload.wikimedia.org/wikipedia/commons/thumb/f/fe/Shopee.svg/256px-Shopee.svg.png', 'd:\\PROJECT-APPS-NATIVE\\POS\\app\\src\\main\\res\\drawable\\ic_shopeefood.png')
download('https://upload.wikimedia.org/wikipedia/en/thumb/a/a9/TikTok_logo.svg/256px-TikTok_logo.svg.png', 'd:\\PROJECT-APPS-NATIVE\\POS\\app\\src\\main\\res\\drawable\\ic_tiktokgo.png')
