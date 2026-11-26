# Unlock FPS

Türkçe · [English](README.en.md)

Unlock FPS, MIUI ve HyperOS cihazlarda oyunlar ve uygulamalar için hedef ekran yenileme hızını PowerKeeper’a iletir. Root gerektirmez. Oyunda ulaşılabilen FPS, oyunun kendi kare hızı sınırına ve cihazın desteğine bağlıdır.

## Kurulum

Android 8.0 veya üzeri ve Xiaomi’nin Pil ve Performans (PowerKeeper) bileşeni gerekir. [Son sürümden UnlockFPS.apk dosyasını indir](https://github.com/JrSpatt/UnlockFPS/releases/latest).

## Kullanım

1. Hedef yenileme hızını seç. Seçicide ekranın bildirdiği hızlar gösterilir.
2. Profilde yer alacak uygulamaları işaretle. Sistem uygulamaları **Sistem** sekmesindedir. Bir uygulamaya özel hız seçmek için adına uzun bas.
3. **Uygula**’ya dokun. Widget, en son kaydedilen profili yeniden gönderir.

**Otomatik gönderim** açıkken profil ekran açık olduğu sürece 15 saniyede bir yeniden gönderilir. **Beast Mode**, sistem uygulamaları dahil başlatılabilen tüm uygulamalar için ekranın en yüksek yenileme hızını ister. Bu modda otomatik gönderim aralığı 60 saniyedir.

## Derleme

GitHub Actions, JDK 17, Android SDK 35 ve Build Tools 35.0.0 kullanır. İmzalı sürümler için depo secret’ları `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` ve `KEY_PASSWORD` olarak tanımlanır. Bir `v*` etiketi gönderildiğinde **Build APK** iş akışı APK’yı derler ve GitHub Release’e ekler.

Yerel test derlemesinde `ANDROID_HOME` Android SDK dizinini göstermelidir.

```sh
ALLOW_DEBUG_KEY=1 bash tools/build.sh
```

APK çıktısı `build/UnlockFPS.apk` konumundadır.

[Değişiklik geçmişi](CHANGELOG.md)
