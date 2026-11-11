# Unwind Launcher, fork dengan waktu solat dan berita

Fork dari The Unwind Launcher v2.1 oleh hazir6677 (lesen MIT). Semua
tingkah laku Unwind yang asal dikekalkan; fork ini hanya menambah tiga
ciri dalam Home, dibina terus dalam launcher, bukan sebagai overlay
atau app kedua.

## Ciri baharu

- Kad waktu solat di Home, kanan atas, sejajar dengan top pill. Waktu
  dari JAKIM zon WLY01 Putrajaya, 5 waktu fardhu sahaja, solat
  seterusnya ditanda dengan pill warna accent wallpaper. Tiada ikon jam.
  Waktu dalam format 12 jam tanpa sifar depan ("5:48 AM", "1:00 PM").
  Sebelah PUTRAJAYA, dijajar kanan, ada tarikh Hijrah rasmi JAKIM
  ("1 Jamadilakhir 1448H", bold, gaya teks sama dengan lokasi) dari
  medan "hijri" dalam API e-solat yang sama; disembunyi bila data
  tiada, supaya tarikh yang salah tidak pernah dipapar. Ada garis
  pemisah nipis di bawah baris lokasi, seperti dalam gambar rujukan.
- Ticker berita di bawah baris app. Label "BREAKING NEWS  |  " kekal
  statik di kiri (bold), hanya headline sahaja yang bergerak
  kanan-ke-kiri pada 35 dp/s dan loop tanpa putus. 20 headline dari
  Berita Harian, Utusan, CNN (melalui Google News) dan Al Jazeera.
- Tarikh dan cuti di sebelah teks greeting: "Good morning |
  November 11, 2026 • Wednesday", ditambah " | Christmas" bila hari
  itu cuti atau perayaan di Malaysia (dari kalendar awam Google
  "Holidays in Malaysia", nama seperti yang kalendar beri). Tarikh
  guna Text yang sama dengan greeting, jadi warna dan font sama
  dengan greeting. Tiada cuti = tarikh sahaja.
- Banner solat 30 saat atas sebarang app bila masuk waktu fardhu, sekali
  setiap solat setiap hari, 5 fardhu sahaja. Teks: "Sekarang sudah masuk
  waktu solat fardhu _____. Solatlah sebelum anda disolatkan!"
- Kad dan ticker guna glass dan warna wallpaper Unwind sendiri, jadi
  tukar wallpaper atau glass style dalam Preferences akan tukar warna
  mereka secara automatik. Tiada setting warna berasingan.
- Preferences ada seksyen "Waktu solat": toggle kad, toggle ticker, dan
  butang "Benarkan banner solat" (hanya muncul bila overlay belum
  dibenarkan).
- Bila IconStyle Themed, tile yang tidak fokus kekal themed; tile yang
  fokus tunjuk ikon asal penuh warna. Bila IconStyle Original, semua
  tile kekal asal seperti dahulu.

## Cara build (GitHub Actions)

Fork ini tidak boleh dibina di komputer biasa tanpa Android SDK penuh.
Build jalan di GitHub Actions melalui `.github/workflows/build.yml`:

1. Cipta repo GitHub baharu dan push semua fail fork ini.
2. Build bermula automatik pada setiap push (atau dari tab Actions,
   "Run workflow").
3. Muat turun APK dari Artifacts: `app-release.apk` (lebih lancar) atau
   `app-debug.apk`.
4. Pilihan: tetapkan GitHub Secrets `UNWIND_KEYSTORE_B64`,
   `UNWIND_KEYSTORE_PASSWORD`, `UNWIND_KEY_ALIAS` dan
   `UNWIND_KEY_PASSWORD` supaya build seterusnya boleh install atas
   build lama tanpa uninstall. Tanpa secret ini, setiap build guna key
   debug sementara.

## Cara pasang

1. Uninstall Unwind v2.1 asal dahulu, kerana APK fork ditandatangan
   dengan key berbeza dan Android tidak benarkan update atas key lain.
2. Setting semasa akan hilang: wallpaper, glass style dan favourites
   perlu dipilih semula selepas pasang.
3. Pasang APK fork (sideload melalui ADB atau USB).
4. Pilih Unwind sebagai Home. Benarkan overlay untuk banner solat bila
   diminta, sama ada dari Preferences (seksyen "Waktu solat") atau
   tetapan sistem.
5. Version: 2.2, versionCode 100 + nombor build GitHub.

## Fail baharu dan diubah

- `Prayer.kt`: fetch dan cache waktu solat JAKIM, format 12 jam,
  kira solat seterusnya.
- `News.kt`: fetch dan cache 4 sumber RSS, parse dengan XmlPullParser.
- `Holidays.kt`: fetch dan cache kalendar cuti Malaysia (ICS awam
  Google), format tarikh English "MMMM d, yyyy • EEEE" + " | cuti".
- `PrayerBannerService.kt`: foreground service dan banner overlay
  30 saat.
- `BootReceiver.kt`: mula servis banner selepas reboot.
- `MainActivity.kt`: mula servis banner, muat data solat dan berita,
  hantar ke HomeContent.
- `Screens.kt`: kad solat dan ticker dalam HomeContent, seksyen
  "Waktu solat" dalam Preferences.
- `Components.kt`: Tile hantar status fokus kepada content; tile fokus
  tunjuk ikon asal bila IconStyle Themed.
- `Model.kt`: setting `showPrayer` dan `showTicker` (default true).
- `AndroidManifest.xml`: permission overlay, foreground service dan
  boot, daftar servis dan receiver.
- `build.yml` dan `app-build.gradle.kts` tidak diubah.

## Had yang diketahui

- Build belum disahkan oleh compiler di sini; semakan manual sahaja
  dibuat (lihat laporan). Build pertama di GitHub Actions adalah
  pengesahan sebenar.
- Dalam layout Stacked dengan banyak favourite, baris app atas boleh
  meluncur di bawah kad solat. Layout Endless (default) tidak terjejas.
- Banner solat tidak muncul bila TV tidur, dan waktu yang terlepas
  semasa tidur tidak ditunjuk lewat.
- Ikon yang tiada mask dan tiada latar yang baik mungkin nampak kurang
  kemas bila fokus dalam mod Themed; itu had data ikon asal, bukan
  perubahan pada ikon.
