# Android Photo Frame & Local Upload Server

A native, lightweight Android application that converts any Android tablet (specifically designed and optimized for low-spec devices like the **Amazon Fire 7**, running Android 9 / Fire OS 7) into a premium digital photo frame. 

It runs an embedded HTTP server (NanoHTTPD) that allows you to drag-and-drop new photos onto a local web panel, or upload them instantly using your phone's native Share Sheet.

---

## 🌟 Key Features

* **Widescreen Slideshow**: Double-buffered smooth cross-dissolve transitions with subtle Ken Burns zoom and pan animations.
* **Auto-Start on Boot**: Launches the photo frame automatically into fullscreen immersive mode whenever the tablet is powered on or restarted.
* **Smart Orientation Rendering**:
  * **Landscape Photos**: Scaled to fill the widescreen display (`cover`).
  * **Portrait Photos**: Automatically detected at runtime and scaled to fit (`contain`), layered over a color-coordinated, heavily-blurred background version of the same image to eliminate blank side columns (similar to high-end TV screensavers).
* **EXIF Auto-Rotation**: Inspects EXIF headers in the Kotlin backend and physically rotates decoded image bitmaps before caching/compressing. Portrait photos will never render sideways or upside-down.
* **Ultra-Fast Metadata Caching**: Utilizes a local JSON-based metadata cache (`metadata_cache.json`) to track photo dimensions, dates, and locations. Reduces API response times from 30+ seconds (EXIF parsing) to under **5 milliseconds**, preventing WebUI hangs and memory leaks.
* **No-Overlap Widgets**: Floating widgets (glassmorphic clock/date and location overlays) dynamically relocate on screen orientation change (clock to top-left, location to bottom-right) to prevent overlaps on portrait/vertical displays.
* **Interactive QR Onboarding**: Shows setup instructions and a local connection QR code when empty. Once running, tapping the clock widget slides in a QR code modal that opens the admin page on any scanning phone.
* **Automatic Format Converter**: Automatically converts uploaded non-standard file formats (such as iOS HEIC/HEIF or extensionless file payloads) into standard JPEG on-the-fly.

---

## 🛠️ Technology Stack

* **Android / Kotlin (Backend)**:
  * Targets API level 34 (Android 14) with backward compatibility down to API level 28 (Android 9 / Fire OS 7).
  * **NanoHTTPD**: A lightweight, zero-dependency embedded HTTP server serving REST APIs and static frontend files on port `8080`.
  * **AndroidX ExifInterface**: Used to parse EXIF metadata tags from incoming image streams.
* **Web Frontend**:
  * Clean, framework-free Vanilla HTML5, CSS3, and ES6 JavaScript.
  * **qrcode.min.js**: Packaged locally for 100% offline-capable QR generation.
  * **OpenStreetMap Nominatim Client**: Asynchronously resolves GPS coordinates to place names directly in the browser using a client-side cache.

---

## 💾 Installation Guide (Amazon Fire / Android Tablet)

### Step 1: Enable USB Debugging on the Tablet
1. On your Amazon Fire tablet, open **Settings**.
2. Go to **Device Options** > **About Fire Tablet**.
3. Locate the **Serial Number** and tap it **7 times** consecutively. A message will appear saying *"You are now a developer"*.
4. Go back to the **Device Options** menu, tap **Developer Options**, and turn on **USB Debugging**.

### Step 2: Install ADB (Android Debug Bridge) on your Computer
* **macOS (via Homebrew)**:
  ```bash
  brew install android-platform-tools
  ```
* **Windows**: Download the SDK Platform-Tools from Google's Android Developer website, extract the ZIP, and add the folder to your system PATH.

### Step 3: Install the APK
1. Connect the tablet to your computer via USB.
2. If prompted on the tablet screen, tap **Allow/Authorize USB Debugging**.
3. Run the following command in your terminal from the folder containing the APK:
   ```bash
   adb install -r photoframe-debug.apk
   ```
4. Find the **Photo Frame** app in your tablet's app drawer and open it once. (Launching it manually at least once is required by Android so the auto-start boot receiver can exit the "stopped" state and start listening for boot completion).

---

## 📲 How to Connect and Upload Photos

### Method 1: Web App Dashboard (Universal)
1. Tap the clock widget on the photo frame to show the QR code.
2. Scan it with your phone's camera to open `http://<TABLET_IP>:8080/admin` in Safari (iOS) or Chrome (Android).
3. **Add to Home Screen**:
   * **iOS (Safari)**: Tap the **Share** button at the bottom of Safari, then tap **Add to Home Screen**.
   * **Android (Chrome)**: Tap the menu dots in Chrome, then tap **Add to Home Screen**.
4. Open the home screen app icon, click **Browse Files** (or drag and drop), select your photos, and upload!

---

### Method 2: iOS Share Sheet Shortcut (iPhone / iPad)

You can set up an Apple Shortcut to send photos directly from your Photos app in two taps.

#### Option A: Static IP Method (Fastest - Single Tap)
*Note: This method works immediately without opening the camera. It requires setting a DHCP Reservation / Static IP for the tablet on your Wi-Fi router.*

1. Open the **Shortcuts** app on your iPhone and tap **`+`** (top-right) to create a new shortcut. Rename it to **"Send to Photo Frame"**.
2. Tap the **Info (i)** or **Settings** button and enable **"Show in Share Sheet"**.
3. Set the top trigger block to: **"Receive `Images` from `Share Sheet`"** (Set *"if there's no input"* to *"Ask for Images"*).
4. Tap **Add Action**, search for **"URL"** (under Web), and add it:
   - Set the URL text to: `http://<TABLET_IP>:8080/api/upload` (Replace `<TABLET_IP>` with your tablet's current IP address, e.g. `http://192.168.1.73:8080/api/upload`).
5. Search for **"Repeat with Each"** (under Scripting) and add it below the URL action.
6. Search for **"Get Contents of URL"** (under Web) and drag it **inside** the repeat loop:
   - Set it to: Get contents of **`URL`** (from step 4).
   - Expand the block's settings (tap the dropdown arrow):
     * **Method**: Change to **POST**
     * **Request Body**: Change to **Form**
     * Tap **Add new field** > choose **File**.
     * Set the key to **`file`** (exact lowercase spelling).
     * Set the value to **`Repeat Item`** (the photo currently in the loop).
7. Tap **Done** to save. Now, select photos in your Photos App, tap **Share** > **Send to Photo Frame** to upload instantly in the background!

#### Option B: QR-Scan Method (Dynamic IP)
*Use this if your tablet's IP address changes frequently and you do not have a static IP set up.*

1. Follow steps 1-3 from Option A above.
2. Search for the **"Scan QR or Barcode"** action and add it below the top block.
3. Search for the **"Replace Text"** action and add it below:
   - Find `/admin` in the scanned `QR/Barcode` and replace with `/api/upload`.
4. Search for the **"Repeat with Each"** action and add it below.
5. Drag a **"Get Contents of URL"** action block inside the repeat loop:
   - Set it to: Get contents of **`Replaced Text`**.
   - Expand settings: Method = **POST**, Request Body = **Form**.
     * Add new field: File -> Key = **`file`**, Value = **`Repeat Item`**.
6. Save the shortcut. To upload, select photos, tap **Share** > **Send to Photo Frame**, and point the camera at the tablet's QR code.

---

### Method 3: Android Share Sheet Integration
1. Install the free, open-source app **"HTTP Request Shortcuts"** from the Google Play Store on your phone.
2. Create a new shortcut and select **"Shortcut to send files/text"**.
3. Configure the shortcut details:
   - **Name**: "Send to Photo Frame"
   - **Method**: `POST`
   - **URL**: `http://<TABLET_IP>:8080/api/upload`
   - **Body Type**: `form-data`
   - Add a parameter with Name = **`file`** and Value = the input file placeholder.
4. Enable **"Show in Share Sheet"** / **"Receive Files"** (file type `image/*`) in settings and save.
5. Open your Android gallery, select photos, tap **Share**, and select **"HTTP Shortcuts"** to upload.

