# Unlock FPS (v1)

Xiaomi / Redmi / POCO (HyperOS, MIUI) telefonlarda oyunların yenileme hızı sınırını kaldıran, root gerektirmeyen uygulama.
Paket adı: `com.spatt.unlockfps` · sürüm: `1.0` (versionCode 1)

## Doldurulacak bağlantılar
Hepsi `src/com/spatt/unlockfps/Config.java` içinde. Varsayılan repo: `JrSpatt/UnlockFPS` (public olmalı). Farklı ad kullanırsan sadece `REPO` satırını değiştir.

| Alan | Ne yapar |
|---|---|
| `GAMES_JSON_URL` | Oyun listesini günde bir GitHub'dan indirir (boşsa sadece uygulamadaki liste) |
| `RELEASES_API_URL` | "Güncellemeleri denetle" için son sürüm adresi |
| `RELEASES_PAGE_URL` | Güncelleme çıkınca / denetim başarısız olunca açılan sayfa |
| `GAME_LIST_URL` | Hakkında ekranındaki "Oyun listesi" satırı |

Oyun listesini değiştirmek için `data/games.json` dosyasını düzenle ve `version` sayısını artır.

## Derleme (GitHub Actions, bilgisayar gerekmez)
1. Kodu yeni bir GitHub reposuna gönder.
2. Repo → Settings → Secrets and variables → Actions içine ekle:
   `KEYSTORE_BASE64` (Termux: `base64 -w0 anahtar.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Actions → "Build APK" → Run workflow. APK, çalışmanın "Artifacts" bölümünde `UnlockFPS` adıyla çıkar.
4. `v1.0` gibi bir etiket (tag) gönderirsen APK otomatik olarak o sürüme eklenir.

Anahtar yoksa derleme durur. Elle çalıştırmada (Run workflow) geçici debug anahtarlı test APK üretilir; etiketli sürümde asla.

## Beast mode
Hero kartında hız seçicinin yanındaki alev logosu. Açıkken tüm başlatılabilir uygulamalara (sistem dahil)
panelin gerçekten bildirdiği en yüksek hızı gönderir. Panel modları okunamazsa Beast açılmaz.

Kapatınca hiçbir uygulamaya istek gönderilmez (eski hızı geri yükleyen bir istek yok). Beast ile yükseltilen
uygulamalar, PowerKeeper yeniden başlayana (genelde telefon yeniden açılana) kadar yüksek hızda kalabilir.

## Panel ve kayıtlı hızlar
Kayıtlı hedef hız (ve oyun başına hız) her kullanımda panelin o anki mod listesine göre doğrulanır; listede yoksa
en yakın düşük hıza indirilir. Panel hiç okunamazsa hiçbir hız gönderilmez ve arayüzde "Panel algılanamadı" yazar.
FPS testi yalnızca kendi ekranının paketine istek gönderir.
