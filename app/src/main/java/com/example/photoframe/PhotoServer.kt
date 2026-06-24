package com.example.photoframe

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale

data class PhotoMetadata(
    val name: String,
    val dateTime: String?,
    val location: String?
)

class PhotoServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    private val tag = "PhotoServer"
    private val photosDir = context.getExternalFilesDir("photos") ?: File(context.filesDir, "photos")
    private val thumbnailsDir = context.getExternalFilesDir("thumbnails") ?: File(context.cacheDir, "thumbnails")
    private val thumbCacheDir = File(thumbnailsDir, "thumb")
    private val slideshowCacheDir = File(thumbnailsDir, "slideshow")

    init {
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
        if (!thumbCacheDir.exists()) {
            thumbCacheDir.mkdirs()
        }
        if (!slideshowCacheDir.exists()) {
            slideshowCacheDir.mkdirs()
        }
        Log.d(tag, "Photos directory initialized at: ${photosDir.absolutePath}")
        Log.d(tag, "Thumbnails directory initialized at: ${thumbnailsDir.absolutePath}")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        Log.d(tag, "Received request: $method $uri")

        return try {
            when {
                // 1. API - Server/Device Info
                uri == "/api/info" && method == Method.GET -> {
                    val ip = getWifiIpAddress() ?: "Unknown"
                    val count = getPhotoCount()
                    val json = """{"ip":"$ip","port":$listeningPort,"photoCount":$count}"""
                    newFixedLengthResponse(Response.Status.OK, "application/json", json)
                }

                // 2. API - List Photos
                uri == "/api/photos" && method == Method.GET -> {
                    val photosMetadataList = getPhotosMetadataList()
                    val json = photosMetadataList.joinToString(separator = ",", prefix = "[", postfix = "]") { meta ->
                        val dateStr = meta.dateTime?.let { "\"$it\"" } ?: "null"
                        val locStr = meta.location?.let { "\"$it\"" } ?: "null"
                        """{"name":"${meta.name}","dateTime":$dateStr,"location":$locStr}"""
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", json)
                }

                // 3. API - Upload Photo
                uri == "/api/upload" && method == Method.POST -> {
                    handleUpload(session)
                }

                // 4. API - Delete Photo
                uri == "/api/delete" && method == Method.POST -> {
                    handleDelete(session)
                }

                // 5. Serve Uploaded Image
                uri.startsWith("/photos/") -> {
                    val filename = uri.substring("/photos/".length)
                    val sizeParam = session.parms["size"] ?: ""
                    val isThumbnail = session.parms["thumb"] == "true" || session.parms["thumbnail"] == "true"
                    val targetSize = when {
                        isThumbnail || sizeParam == "thumb" -> 250
                        sizeParam == "slideshow" -> 1200
                        else -> -1
                    }
                    servePhotoFile(filename, targetSize)
                }

                // 6. Serve Static Web Assets
                else -> {
                    serveStaticAsset(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error serving request", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error: ${e.message}")
        }
    }

    private fun getPhotosList(): List<String> {
        val extensions = listOf("jpg", "jpeg", "png", "webp", "gif")
        return photosDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in extensions }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    private fun getPhotoCount(): Int {
        return getPhotosList().size
    }

    private fun getPhotosMetadataList(): List<PhotoMetadata> {
        val extensions = listOf("jpg", "jpeg", "png", "webp", "gif")
        return photosDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in extensions }
            ?.sortedBy { it.name }
            ?.map { file -> getPhotoMetadata(file) }
            ?: emptyList()
    }

    private fun getPhotoMetadata(file: File): PhotoMetadata {
        try {
            val exifInterface = ExifInterface(file.absolutePath)
            
            // 1. Extract Date Time
            val dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
            val formattedDate = formatExifDate(dateTime)

            // 2. Extract GPS Coordinates & Reverse Geocode
            var locationString: String? = null
            val latLong = FloatArray(2)
            if (exifInterface.getLatLong(latLong)) {
                val latitude = latLong[0].toDouble()
                val longitude = latLong[1].toDouble()
                
                locationString = if (Build.VERSION.SDK_INT >= 24 && Geocoder.isPresent()) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val city = address.locality ?: address.subAdminArea ?: address.adminArea
                            val country = address.countryName
                            if (city != null && country != null) {
                                "$city, $country"
                            } else {
                                city ?: country
                            }
                        } else {
                            String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
                        }
                    } catch (e: Exception) {
                        String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
                    }
                } else {
                    String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
                }
            }

            return PhotoMetadata(file.name, formattedDate, locationString)
        } catch (e: Exception) {
            Log.e(tag, "Failed to read EXIF for ${file.name}", e)
            return PhotoMetadata(file.name, null, null)
        }
    }

    private fun formatExifDate(rawDateTime: String?): String? {
        if (rawDateTime == null) return null
        try {
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val date = parser.parse(rawDateTime) ?: return rawDateTime
            val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.US)
            return formatter.format(date)
        } catch (e: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy:MM:dd", Locale.US)
                val date = parser.parse(rawDateTime.split(" ")[0]) ?: return rawDateTime
                val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.US)
                return formatter.format(date)
            } catch (e2: Exception) {
                return rawDateTime
            }
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val parms = session.parms
            
            // Look for the file key (usually "file" in HTML forms)
            val tempPath = files["file"]
            val originalName = parms["file"]
            
            if (tempPath == null || originalName == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing file content")
            }

            // Sanitize the file name to prevent path traversal and odd characters
            val sanitizedName = originalName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            if (sanitizedName.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid file name")
            }

            // Verify extension
            val ext = sanitizedName.substringAfterLast(".").lowercase()
            val validExts = listOf("jpg", "jpeg", "png", "webp", "gif")

            val tempFile = File(tempPath)
            var finalName = sanitizedName

            if (ext in validExts) {
                val destFile = File(photosDir, sanitizedName)
                // Copy file to permanent location
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
                Log.i(tag, "Successfully uploaded: $sanitizedName")
            } else {
                // Attempt to decode as bitmap (handles HEIC, HEIF, and generic filenames without extensions)
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
                    if (bitmap != null) {
                        val nameWithoutExt = if (sanitizedName.contains(".")) {
                            sanitizedName.substringBeforeLast(".")
                        } else {
                            sanitizedName
                        }
                        val jpgName = "$nameWithoutExt.jpg"
                        val destFile = File(photosDir, jpgName)
                        
                        FileOutputStream(destFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        bitmap.recycle()
                        tempFile.delete()
                        Log.i(tag, "Successfully converted and saved image as JPEG: $jpgName")
                        finalName = jpgName
                    } else {
                        tempFile.delete()
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Unsupported file format. Only images are allowed.")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to decode/convert uploaded file", e)
                    tempFile.delete()
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Unsupported file format. Only images are allowed.")
                }
            }

            // Allow CORS for easy testing
            val response = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"success","filename":"$finalName"}""")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            Log.e(tag, "Upload failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${e.message}")
        }
    }

    private fun handleDelete(session: IHTTPSession): Response {
        // NanoHTTPD parses body to parms if it is a standard form POST or query params
        val files = HashMap<String, String>()
        session.parseBody(files)
        val name = session.parms["name"]
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing filename to delete")

        // Sanitize name
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val targetFile = File(photosDir, sanitizedName)

        return if (targetFile.exists() && targetFile.isFile) {
            if (targetFile.delete()) {
                Log.i(tag, "Deleted photo: $sanitizedName")
                val thumbFile = File(thumbCacheDir, sanitizedName)
                if (thumbFile.exists() && thumbFile.isFile) {
                    thumbFile.delete()
                }
                val slideshowFile = File(slideshowCacheDir, sanitizedName)
                if (slideshowFile.exists() && slideshowFile.isFile) {
                    slideshowFile.delete()
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"success"}""")
            } else {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to delete file")
            }
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }
    }

    private fun servePhotoFile(filename: String, targetSize: Int): Response {
        val sanitizedName = filename.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val file = File(photosDir, sanitizedName)

        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Image not found")
        }

        val mimeType = resolveMimeType(sanitizedName)

        if (targetSize > 0) {
            val cacheDir = if (targetSize <= 250) thumbCacheDir else slideshowCacheDir
            val cachedFile = File(cacheDir, sanitizedName)
            if (cachedFile.exists() && cachedFile.isFile) {
                return newChunkedResponse(Response.Status.OK, mimeType, FileInputStream(cachedFile))
            }

            // Generate and cache scaled image on-the-fly
            try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)

                var scale = 1
                while (options.outWidth / scale / 2 >= targetSize && options.outHeight / scale / 2 >= targetSize) {
                    scale *= 2
                }

                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = scale
                }
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)

                if (bitmap != null) {
                    FileOutputStream(cachedFile).use { out ->
                        val format = when (mimeType) {
                            "image/png" -> android.graphics.Bitmap.CompressFormat.PNG
                            "image/webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                            else -> android.graphics.Bitmap.CompressFormat.JPEG
                        }
                        bitmap.compress(format, 80, out)
                    }
                    bitmap.recycle()
                    Log.d(tag, "Generated cached photo ($targetSize px) for: $sanitizedName")
                    return newChunkedResponse(Response.Status.OK, mimeType, FileInputStream(cachedFile))
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to generate scaled photo ($targetSize px) for $sanitizedName", e)
            }
        }

        return newChunkedResponse(Response.Status.OK, mimeType, FileInputStream(file))
    }

    private fun serveStaticAsset(uri: String): Response {
        // Clean up URI path to map to assets
        val cleanUri = when (uri) {
            "/", "/index.html" -> "/index.html"
            "/admin", "/admin/" -> "/admin.html"
            else -> if (uri.endsWith("/")) uri + "index.html" else uri
        }
        val assetPath = "web" + cleanUri

        return try {
            val inputStream = context.assets.open(assetPath)
            val mimeType = resolveMimeType(assetPath)
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: FileNotFoundException) {
            Log.w(tag, "Asset not found: $assetPath")
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Asset not found")
        } catch (e: IOException) {
            Log.e(tag, "Error reading asset: $assetPath", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading asset")
        }
    }

    private fun resolveMimeType(filename: String): String {
        val ext = filename.substringAfterLast(".").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    private fun getWifiIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        val isIPv4 = ip.indexOf(':') < 0
                        if (isIPv4) {
                            return ip
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(tag, "Failed to get IP address", ex)
        }
        return null
    }
}
