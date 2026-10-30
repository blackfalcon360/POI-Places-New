# PoiPlaces

Android app (Java only, no AndroidX, no external libraries) to save points of interest (POIs), see nearby places,
keep cities and nearby places offline for GPS use, and follow your position in the background with a notification.
Package: `com.blackfalcon.poiplaces`. UI languages: English (default), Urdu, Arabic.

## What the app does
- **Top bar**: current place name (for example "2.3 km SW of Risalpur"), coordinates, accuracy and altitude,
  the nearest city, and a small compass (true north, using the phone's magnetic sensor and the local declination).
- **Nearby tab**: places around you from the offline data, nearest first, with distance and direction.
  Filters: all / cities & villages / services; radius 5 / 25 / 100 / 500 km. Tap a place to open it in a maps app;
  tap "+" to save it as a POI. "Update nearby places (online)" downloads the 5 km area around you.
- **Saved tab**: your POIs with category, note, distance and direction. "Save here" saves the current position,
  "Add coordinates" saves a typed position (for example `34.0123, 71.9876`, `34.0123N 71.9876E`, or degrees-minutes-seconds).
  Tap a POI to open it in a maps app; tap the menu (or long-press) to edit, copy coordinates, share or delete.
  Sort by nearest, newest or A-Z.
- **Import / export**: GPX (waypoints), KML, GeoJSON and CSV. Export lets you choose the format and where to save the file;
  import detects the format from the file content (not the file name) and skips points that are already saved.
- **Offline tab**: about 225 built-in major cities (approximate positions) work with no download. You can also download
  cities/villages and/or services (mosques, hospitals, pharmacies, fuel, food, banks, schools, police, markets, hotels,
  attractions) around your position (10 / 25 / 50 km) from OpenStreetMap. Downloaded areas are listed and can be deleted.
  Place names and distances then work with no internet.
- **Background**: a foreground service keeps following your position after you press Home and shows the place name,
  coordinates, nearest city and nearest saved POI in a notification (with a Stop button). It can also start after the phone boots (optional).
- **Settings**: language (English default), dark/light theme, background on/off, update interval, start on boot,
  online features on/off, Save button, shortcuts for location "Allow all the time", battery settings and app permissions.
- Exit confirmation on Back; "By: Black Falcon" on the main screen; Splash screen first.

## How to build
1. Upload the contents of this folder to a GitHub repository (the folder with `build.gradle` and `.github` must be the repository root).
2. Open the **Actions** tab and run **Build APK** (it also runs on every push).
3. Download the `PoiPlaces-APK` artifact and install the APK.

## Privacy
- Your position stays on the phone. Downloads (Offline tab and "Update nearby places") send the center of the chosen area,
  which is your approximate position, to the public OpenStreetMap Overpass servers, only when you press the button.
  Turn off "Online features" in Settings to never contact the internet.
- Map apps opened from a list receive that place's coordinates. Nothing else is sent.

## Limitations (honest list)
- The current place name comes from your offline data. With no downloaded area it falls back to the built-in major cities,
  so it can be far away. Download your area (Offline tab) for village-level names.
- The built-in city list is small and approximate (about 0.01 to 0.1 degree).
- Offline data is OpenStreetMap data: names, coverage and category tags depend on what mappers entered, and names may be in
  English or the local language. The downloader uses the public Overpass servers, which can be busy or time out for large areas;
  use 10 to 25 km. The download code was written from the public documentation and was not tested against the live servers.
- No offline map pictures: places are shown as lists. Tapping a place opens an installed maps app, which needs its own offline maps.
- Background tracking: Android may stop it on some phones. Allow notifications, choose location "Allow all the time",
  remove battery restrictions, and on **Xiaomi / Oppo / Vivo / Huawei** enable **Autostart**.
  The foreground service shows a permanent notification because Android requires it.
- Compass: needs a magnetic sensor; metal and magnets near the phone change the reading. Calibrate by moving the phone in a figure 8.
- Export file names and types are chosen with Android's file picker; some file managers may add an extra extension.
- Import reads waypoints/points only (GPX `wpt`, KML Placemark Point, GeoJSON Point/MultiPoint, CSV rows). Tracks, routes, lines and polygons are skipped.

## اردو (مختصر)
- اوپر موجودہ جگہ کا نام (مثلاً "رسالپور سے 2.3 کلومیٹر جنوب مغرب")، کوآرڈینیٹ اور چھوٹا کمپاس دکھائی دیتا ہے۔
- قریبی ٹیب میں آس پاس کے مقامات فاصلے اور سمت کے ساتھ ہیں؛ کسی پر دبانے سے نقشہ ایپ کھلتی ہے۔
- محفوظ ٹیب میں آپ کے POI ہیں؛ GPX، KML، GeoJSON اور CSV میں امپورٹ/ایکسپورٹ ہو سکتے ہیں۔
- آف لائن ٹیب: تقریباً 225 بڑے شہر پہلے سے شامل ہیں؛ اپنے علاقے کے شہر، گاؤں اور سہولیات OpenStreetMap سے ڈاؤن لوڈ کر کے انٹرنیٹ کے بغیر استعمال کریں۔
- ہوم دبانے کے بعد بھی ایپ پس منظر میں چلتی ہے اور نوٹیفیکیشن میں جگہ کا نام دکھاتی ہے۔ Xiaomi/Oppo/Vivo/Huawei میں Autostart آن کریں اور بیٹری کی پابندی ہٹائیں؛ لوکیشن میں "Allow all the time" دیں۔
- آپ کا مقام فون سے باہر نہیں جاتا؛ ڈاؤن لوڈ کے وقت صرف علاقے کا مرکز Overpass سرور کو جاتا ہے۔ سیٹنگز میں آن لائن فیچرز بند کیے جا سکتے ہیں۔
- یہ ایپ نقشے کی تصاویر آف لائن نہیں دکھاتی؛ صرف مقامات کی فہرست ہے۔
