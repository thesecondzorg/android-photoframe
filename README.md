# Android Photo Frame & Local Upload Server

A native, lightweight Android application that converts any Android tablet (specifically designed and tested on the **Kindle Fire 7**, running Android 9 / API 28) into a premium digital photo frame. 

It runs an embedded HTTP server (NanoHTTPD) that allows you to drag-and-drop new photos onto a local web panel, or upload them instantly using your phone's native Share Sheet.

---

## 🌟 Key Features

* **Widescreen Slideshow**: Double-buffered smooth cross-dissolve transitions with subtle Ken Burns zoom and pan animations.
* **Smart Orientation Resizing**:
  * **Landscape Photos**: Scale to fill the widescreen display (`cover`).
  * **Portrait Photos**: Automatically detected at runtime and scaled to fit (`contain`), layered over a color-coordinated, heavily-blurred background version of the same image to eliminate blank side columns (similar to high-end TV screensavers).
* **EXIF Metadata Reader**: Extracts date and GPS coordinates from image files. Coordinates are dynamically reverse-geocoded into city and country names on the client using the **OpenStreetMap Nominatim API** (with local memory caching).
* **Clock & QR Code Overlay**: An elegant glassmorphic clock widget floats in the corner. Tapping it displays an offline-generated QR code pointing to the Admin Dashboard.
* **Onboarding Setup Screen**: Displays step-by-step connection instructions alongside a setup QR code when the frame's storage is empty.
* **Smart Native Sharing**: Integrates directly with iOS (Apple Shortcuts) and Android (HTTP Request Shortcuts). Allows selecting photos, tapping "Share", and scanning the photo frame's QR code to dynamically resolve the IP address and upload photos in the background (zero maintenance when the tablet's IP changes).
* **Automatic HEIC-to-JPEG Conversion**: Natively decodes iOS HEIC/HEIF images and formats generic extensionless uploads (e.g. `Repeat Item`) into standard JPEG on the fly.
* **Dynamic Layout Protection**: Relocates floating widgets (clock to top-left, details to bottom-right) in vertical tablet orientation to prevent overlaps on narrow screens.

---

## 🛠️ Technology Stack

* **Android / Kotlin (Backend)**:
  * Targets API level 34 (Android 14) with backward compatibility down to API level 28 (Android 9 / Fire OS 7).
  * **NanoHTTPD**: A lightweight, zero-dependency embedded HTTP server serving REST APIs and static frontend files on port `8080`.
  * **AndroidX ExifInterface**: Used to parse EXIF metadata tags from incoming image streams.
* **Web Frontend**:
  * Clean, framework-free Vanilla HTML5, CSS3, and ES6 JavaScript.
  * **qrcode.min.js**: Packaged locally for 100% offline-capable QR generation.
  * **Nominatim Client**: Lightweight reverse-geocoding client with coordinates cache.

---

## 🚀 Building & Running

### Prerequisites
1. Android SDK installed.
2. Java 17 configured in your environment.

### Compile Debug APK
Run the following Gradle command in the root folder:
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install onto Tablet
Connect the tablet via USB with USB Debugging enabled, and run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 How to Connect and Upload Photos

### Method 1: Safari / Chrome Web App (No Apps Needed)
1. Tap the clock widget on the photo frame to show the QR code.
2. Scan it with your phone's camera to open `http://<TABLET_IP>:8080/admin` in Safari (iOS) or Chrome (Android).
3. **Add to Home Screen**:
   * **iOS**: Tap the **Share** button at the bottom of Safari, then tap **Add to Home Screen**.
   * **Android**: Tap the menu dots in Chrome, then tap **Add to Home Screen**.
4. Open the home screen app, click **Browse Files** (or drag and drop), select your photos, and upload!

### Method 2: Smart iOS Share Sheet (Direct from Photos App)
You can set up a custom shortcut that scans the screen QR code to resolve the tablet's IP address dynamically. This means the shortcut never breaks even if your home Wi-Fi reassigns a different IP to the tablet.

1. Open the built-in **Shortcuts** app on your iPhone and tap **`+`** to create a new shortcut named **"Send to Photo Frame"**.
2. Tap the **Info (i)** button (or settings sliders) and enable **"Show in Share Sheet"**.
3. Set up the following action flow:
   * **Receive `Images` from `Shortcut Input`** (if no input, set to "Ask for Images").
   * Add the **"Scan QR or Barcode"** action block.
   * Add a **"Replace Text"** action block: Find `/admin` in `QR/Barcode` and replace it with `/api/upload`.
   * Add a **"Repeat with Each"** action block (repeating items in `Shortcut Input`).
     * Inside the loop, add a **"Get Contents of URL"** action block:
       * Set URL to the output of **"Replace Text"**.
       * Expand options: Method = **POST**, Request Body = **Form**.
       * Add a new File field: Key = **`file`**, Value = **`Repeat Item`** (Pro-tip: add a **"Convert Image"** block to convert to JPEG first to speed up uploads).
4. **To use**: Select photos in the Photos app, tap **Share**, tap **"Send to Photo Frame"**, and point the camera at the tablet's QR code.
