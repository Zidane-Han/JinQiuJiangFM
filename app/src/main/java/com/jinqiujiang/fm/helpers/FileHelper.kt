/*
 * FileHelper.kt
 * Implements the FileHelper object
 * A FileHelper provides helper methods for reading and writing files from and to device storage
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.helpers

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.database.objects.Podcast
import com.jinqiujiang.fm.legacy.LegacyCollection
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URL
import java.text.MessageFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.ln
import kotlin.math.pow


/*
 * FileHelper object
 */
object FileHelper {

    /* Define log tag */
    private val TAG: String = FileHelper::class.java.simpleName


    /* Return an InputStream for given Uri */
    fun getTextFileStream(context: Context, uri: Uri): InputStream? {
        var stream : InputStream? = null
        try {
            stream = context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stream
    }


    /* Get file size for given file-Uri */
    fun getFileSize(uri: Uri): Long {
        // return DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L /* => solution for content-Uris */
        val path: String? = uri.path
        if (path != null) {
            return File(path).length()
        } else {
            return 0L
        }
    }


    /* Get file last modified timestamp for given file-Uri */
    fun getFileLastModified(uri: Uri): Long {
        // return DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L /* => solution for content-Uris */
        val path: String? = uri.path
        if (path != null) {
            return File(path).lastModified()
        } else {
            return 0L
        }
    }


    /* Get file name for given Uri */
    fun getFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: String()
            }
        }
        return String()
    }


    /* Parses out file name from URL */
    fun getFileNameFromUrl(remoteFileLocation: String): String {
        // val fileName: String = remoteFileLocation.substring(remoteFileLocation.lastIndexOf('/')+1, remoteFileLocation.length)
        return URLUtil.guessFileName(remoteFileLocation, null, null)
    }


    /* Get content type for given file */
    fun getContentType(context: Context, uri: Uri): String {
        // get file type from content resolver
        val contentType: String = context.contentResolver.getType(uri) ?: Keys.MIME_TYPE_UNSUPPORTED
//        if (contentType != Keys.MIME_TYPE_UNSUPPORTED && contentType != Keys.MIME_TYPE_OCTET_STREAM) { // todo uncomment if octet stream should be supported
        if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
            // return the found content type
            return contentType
        } else {
            // fallback: try to determine file type based on file extension
            return getContentTypeFromExtension(getFileName(context, uri))
        }
    }


    /* Determine content type based on file extension */
    fun getContentTypeFromExtension(fileName: String): String {
        Log.i(TAG, "Deducing content type from file name: $fileName")
        if (fileName.endsWith("xml", true)) return Keys.MIME_TYPE_XML
        if (fileName.endsWith("rss", true)) return Keys.MIME_TYPE_XML
        if (fileName.endsWith("mp3", true)) return Keys.MIME_TYPE_MP3
        if (fileName.endsWith("m4a", true)) return Keys.MIME_TYPE_MP4
        if (fileName.endsWith("png", true)) return Keys.MIME_TYPE_PNG
        if (fileName.endsWith("jpg", true)) return Keys.MIME_TYPE_JPG
        if (fileName.endsWith("jpeg", true)) return Keys.MIME_TYPE_JPG
        // default return
        return Keys.MIME_TYPE_UNSUPPORTED
    }


    /* Gets / extracts a name for a file (no extension) from an Uri */
    fun getFileNameFromUri(uri: Uri): String {
        val pathSegments: List<String> = uri.pathSegments
        val fileName: String
        // use last path segment as filename
        if (pathSegments.isNotEmpty()) {
            fileName = pathSegments.last()
        }
        // fallback: use second-level domain or a random number as file name
        else {
            val host = uri.host
            if (host.isNullOrBlank()) {
                fileName = ThreadLocalRandom.current().nextInt().toString()
            } else {
                val parts: List<String> = host.split(".")
                if (parts.size >= 2) {
                    fileName = parts.reversed()[1]
                } else {
                    fileName = ThreadLocalRandom.current().nextInt().toString()
                }
            }
        }
        return sanitizeFileSystemName(fileName)
    }


    /* Sanitizes a string for safe use as a single file-system name (folder or file): forces one path segment and neutralizes "."/".." so it can't escape its parent folder */
    private fun sanitizeFileSystemName(name: String): String {
        // collapse path separators into a single path segment
        // note: ":" is intentionally left in place - it is not a path separator on Android, so
        // replacing it adds no traversal protection but would rename existing per-podcast folders
        // (e.g. "Serial: Season 3") and orphan their cover files
        val singleSegment: String = name.replace(Regex("[/\\\\]"), "_")
        // a segment made only of dots (".", "..", …) resolves to a parent/self dir -> neutralize
        return if (singleSegment.isNotEmpty() && singleSegment.all { it == '.' }) "_" else singleSegment
    }


    /* Determines a destination folder */
    fun determineDestinationFolderPath(type: Int, podcastName: String = String()): String {
        val folderPath: String
        val subDirectory: String = sanitizeFileSystemName(podcastName)
        when (type) {
            Keys.FILE_TYPE_RSS -> folderPath = Keys.FOLDER_TEMP
            Keys.FILE_TYPE_AUDIO -> folderPath = Keys.FOLDER_AUDIO + "/" + subDirectory
            Keys.FILE_TYPE_IMAGE -> folderPath = Keys.FOLDER_IMAGES + "/" + subDirectory
            else -> folderPath = "/"
        }
        return folderPath
    }


    /* Clears given folder - keeps given number of files */
    fun clearFolder(folder: File?, keep: Int = 0, deleteFolder: Boolean = false) {
        if (folder != null && folder.exists()) {
            val files: Array<File>? = folder.listFiles()
            if (files != null) {
                val fileCount: Int = files.size
                files.sortBy { it.lastModified() }
                for (fileNumber in files.indices) {
                    if (fileNumber < fileCount - keep) {
                        files[fileNumber].delete()
                    }
                }
                if (deleteFolder && keep == 0) {
                    folder.delete()
                }
            }
        }
    }


    /* Creates a copy of a given uri from downloadmanager - goal is to provide stable Uris */
    fun saveCopyOfFile(context: Context, podcastName: String, tempFileUri: Uri, fileType: Int, fileName: String, async: Boolean = false): Uri {
        val targetFile: File = File(context.getExternalFilesDir(determineDestinationFolderPath(fileType, podcastName)), fileName)
        if (targetFile.exists()) targetFile.delete()
        when (async) {
            true -> {
                // copy file async (= fire & forget - no return value needed)
                CoroutineScope(IO).launch { saveCopyOfFileSuspended(context, tempFileUri, targetFile.toUri()) }
            }
            false -> {
                // copy file
                copyFile(context, tempFileUri, targetFile.toUri(), deleteOriginal = true)
            }
        }
        return targetFile.toUri()
    }


    /* Creates and save a scaled version of the podcast cover */
    fun saveCover(context: Context, podcastName: String, sourceImageUri: String, size: Int, fileName: String, timestamp: Long): Uri {
        val coverImageFile: File = File(context.getExternalFilesDir(determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcastName)), fileName)
        // load and scale the image
        try {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(sourceImageUri)
                .signature(ObjectKey(timestamp))
                .override(size, size)
                .centerCrop()
                .error(R.drawable.podcast_cover)
                .submit()
                .get() // This blocks until the image is loaded - use only on background thread
            writeImageFile(bitmap, coverImageFile, Bitmap.CompressFormat.JPEG, quality = 75)
        } catch (e: Exception) {
            val defaultBitmap = ContextCompat.getDrawable(context, R.drawable.podcast_cover)!!.toBitmap(size, size)
            writeImageFile(defaultBitmap, coverImageFile, Bitmap.CompressFormat.JPEG, quality = 75)
        }
        // manually set the last modified timestamp for the Glide signature cache tag
        coverImageFile.setLastModified(timestamp)
        return coverImageFile.toUri()
    }

//    fun saveCover(context: Context, podcastName: String, sourceImageUri: String, size: Int, fileName: String): Uri {
//        val coverBitmap: Bitmap = ImageHelper.getPodcastCoverx(context, sourceImageUri, size)
//        val file: File = File(context.getExternalFilesDir(determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcastName)), fileName)
//        writeImageFile(context, coverBitmap, file, Bitmap.CompressFormat.JPEG, quality = 75)
//        return file.toUri()
//    }


    /* Reads podcast collection from storage using GSON */ // todo remove
    fun readLegacyCollection(context: Context): LegacyCollection {
        Log.v(TAG, "Reading collection - Thread: ${Thread.currentThread().name}")
        // get JSON from text file
        val json: String = readTextFile(context, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE)
        var collection: LegacyCollection = LegacyCollection()
        if (json.isNotBlank()) {
            // convert JSON and return as collection
            try {
                collection = getCustomGson().fromJson(json, LegacyCollection::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return collection
    }


    /* Appends a message to an existing log - and saves it */
    fun saveLog(context: Context, logMessage: String) {
        var log: String = readTextFile(context, Keys.FOLDER_COLLECTION, Keys.DEBUG_LOG_FILE)
        log = "$log $logMessage"
        writeTextFile(context, log, Keys.FOLDER_COLLECTION, Keys.DEBUG_LOG_FILE)
    }


    /* Deletes the debug log file */
    fun deleteLog(context: Context) {
        val logFile: File = File(context.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.DEBUG_LOG_FILE)
        if (logFile.exists()) {
            logFile.delete()
        }
    }


    /* Checks if enough ( = more than 512mb) free space is available */
    fun enoughFreeSpaceAvailable(context: Context): Boolean {
        val usableSpace: Long = context.getExternalFilesDir(Keys.FOLDER_COLLECTION)?.usableSpace ?: 0L
        Log.i(TAG, "usableSpace: $usableSpace")
        return usableSpace > 512000000L
    }


    /* Gets the available space */
    // Credit: https://github.com/osmandapp/OsmAnd/blob/8c6d57468a9ff6ad684c850c9565a6cd32aa4555/OsmAnd/src/net/osmand/plus/download/DownloadActivity.java#L659
    fun getAvailableFreeSpace(context: Context): String {
        val dir: File? = context.getExternalFilesDir("")
        var size: String = String()
        var percent: Int = 0
        if (dir != null && dir.canRead()) {
            val fs: StatFs = StatFs(dir.absolutePath)
            size = formatSize(fs.availableBlocksLong * fs.blockSizeLong)
            percent = 100 - (fs.availableBlocksLong * 100 / fs.blockCountLong).toInt()
        }
        return "${context.getString(R.string.pref_delete_all_storage_space_available)}: $size | ${context.getString(R.string.pref_delete_all_storage_space_used)}: $percent%"
    }


    /* Determines free space in bytes for given folder  */
    // Credit: https://github.com/osmandapp/OsmAnd/blob/8c6d57468a9ff6ad684c850c9565a6cd32aa4555/OsmAnd/src/net/osmand/plus/download/DownloadActivity.java#L659
    fun getAvailableFreeSpaceForFolder(folder: File?): String {
        if (folder != null && folder.canRead() && folder.isDirectory) {
            val fs: StatFs = StatFs(folder.absolutePath)
            return formatSize(fs.availableBlocksLong * fs.blockSizeLong)
        } else {
            return formatSize(0L)
        }
    }


    /* Determines percent of used space for given folder  */
    // Credit: https://github.com/osmandapp/OsmAnd/blob/8c6d57468a9ff6ad684c850c9565a6cd32aa4555/OsmAnd/src/net/osmand/plus/download/DownloadActivity.java#L659
    fun getUsedStorageSpaceForFolder(folder: File?): Int {
        if (folder != null && folder.canRead() && folder.isDirectory) {
            val fs: StatFs = StatFs(folder.absolutePath)
            return (100 - (fs.availableBlocksLong * 100 / fs.blockCountLong)).toInt()
        } else {
            return 100
        }
    }


    /* Returns size of folder in a readable format */
    fun getFolderSize(folder: File?): String {
        val size: Long
        if (folder != null && folder.canRead() && folder.isDirectory) {
            size = calculateFolderSize(folder)
        } else {
            size = 0L
        }
        return formatSize(size)
    }


    /* Returns size of folder (including all subfolders) in Bytes */
    private fun calculateFolderSize(folder: File): Long {
        var size: Long = 0L
        //val fs: StatFs = StatFs(folder.absolutePath)
        //size += fs.blockSizeLong
        val fileList = folder.listFiles()
        if (!fileList.isNullOrEmpty()) {
            fileList.forEach { file ->
                if (file != null && file.canRead()) {
                    if (file.isDirectory) {
                        size += calculateFolderSize(file)
                    } else {
                        size += file.length()
                    }
                }
            }
        }
        return size
    }


    /* Formats bytes into readable strings */
    // Credit: https://github.com/osmandapp/OsmAnd/blob/8c6d57468a9ff6ad684c850c9565a6cd32aa4555/OsmAnd/src/net/osmand/AndroidUtils.java#L237
    private fun formatSize(sizeBytes: Long): String {
        val formatKb = MessageFormat("{0, number,##.#}", Locale.US)
        val formatGb = MessageFormat("{0, number,#.##}", Locale.US)
        val formatMb = MessageFormat("{0, number,##.#}", Locale.US)
        val sizeKb = (sizeBytes + 512 shr 10).toInt()
        val size: String
        var numSuffix: String = "MB"
        if (sizeKb > 1 shl 20) {
            size = formatGb.format(arrayOf<Any>(sizeKb.toFloat() / (1 shl 20)))
            numSuffix = "GB"
        } else if (sizeBytes > 100 * (1 shl 10)) {
            size = formatMb.format(arrayOf<Any>(sizeBytes.toFloat() / (1 shl 20)))
        } else {
            size = formatKb.format(arrayOf<Any>(sizeBytes.toFloat() / (1 shl 10)))
            numSuffix = "kB"
        }
        return "$size $numSuffix"
    }



    /* Suspend function: Wrapper for copyFile */
    suspend fun saveCopyOfFileSuspended(context: Context, originalFileUri: Uri, targetFileUri: Uri): Boolean {
        return suspendCoroutine { cont ->
            cont.resume(copyFile(context, originalFileUri, targetFileUri, deleteOriginal = true))
        }
    }


    /* Copies file to specified target */
    private fun copyFile(context: Context, originalFileUri: Uri, targetFileUri: Uri, deleteOriginal: Boolean = false): Boolean {
        var success: Boolean = true
        try {
            val inputStream = context.contentResolver.openInputStream(originalFileUri)
            val outputStream = context.contentResolver.openOutputStream(targetFileUri)
            if (outputStream != null && inputStream != null) {
                inputStream.copyTo(outputStream)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to copy file.")
            success = false
            exception.printStackTrace()
        }
        if (deleteOriginal) {
            try {
                // use contentResolver to handle files of type content://
                context.contentResolver.delete(originalFileUri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to delete the original file. Stack trace: $e")
            }
        }
        return success
    }


    /*  Creates a Gson object */
    private fun getCustomGson(): Gson {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        gsonBuilder.excludeFieldsWithoutExposeAnnotation()
        return gsonBuilder.create()
    }


    /* Create nomedia file in given folder to prevent media scanning */
    fun createNomediaFile(folder: File?) {
        if (folder != null && folder.exists() && folder.isDirectory) {
            val nomediaFile: File = getNoMediaFile(folder)
            if (!nomediaFile.exists()) {
                val noMediaOutStream: FileOutputStream = FileOutputStream(getNoMediaFile(folder))
                noMediaOutStream.write(0)
            } else {
                Log.v(TAG, ".nomedia file exists already in given folder.")
            }
        } else  {
            Log.w(TAG, "Unable to create .nomedia file. Given folder is not valid.")
        }
    }


    /* Delete nomedia file in given folder */
    fun deleteNoMediaFile(folder: File) {
        getNoMediaFile(folder).delete()
    }


    /* Converts byte value into a human-readable format */
    // Source: https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    fun getReadableByteCount(bytes: Long, si: Boolean = true): String {

        // check if Decimal prefix symbol (SI) or Binary prefix symbol (IEC) requested
        val unit: Long = if (si) 1000L else 1024L

        // just return bytes if file size is smaller than requested unit
        if (bytes < unit) return "$bytes B"

        // calculate exp
        val exp: Int = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()

        // determine prefix symbol
        val prefix: String = ((if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i")

        // calculate result and set number format
        val result: Double = bytes / unit.toDouble().pow(exp.toDouble())
        val numberFormat = NumberFormat.getNumberInstance()
        numberFormat.maximumFractionDigits = 1

        return numberFormat.format(result) + " " + prefix + "B"
    }


    /* Reads InputStream from file uri and returns it as String */
    private fun readTextFile(context: Context, folder: String, fileName: String): String {
        // todo read https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
        // https://developer.android.com/training/secure-file-sharing/retrieve-info

        // check if file exists
        val file: File = File(context.getExternalFilesDir(folder), fileName)
        if (!file.exists() || !file.canRead()) {
            return String()
        }
        // readSuspended until last line reached
        val stream: InputStream = file.inputStream()
        val reader: BufferedReader = BufferedReader(InputStreamReader(stream))
        val builder: StringBuilder = StringBuilder()
        reader.forEachLine {
            builder.append(it)
            builder.append("\n") }
        stream.close()
        return builder.toString()
    }


    /* Writes given text to file on storage */
    private fun writeTextFile(context: Context, text: String, folder: String, fileName: String) {
        if (text.isNotBlank()) {
            try {
                File(context.getExternalFilesDir(folder), fileName).writeText(text)
            } catch (e: Exception) {
                // optional diagnostic output must not take down the app
                Log.w(TAG, "Writing text file $fileName failed. $e")
            }
        } else {
            Log.w(TAG, "Writing text file $fileName failed. Empty text string text was provided.")
        }
    }


    /* Writes given text to file specified by destinationUri */
    private fun writeTextToUri(context: Context, text: String, destinationUri: Uri) {
        if (text.isNotBlank()) {
            val resolver: ContentResolver = context.contentResolver
            val outputStream: OutputStream? = resolver.openOutputStream(destinationUri)
            outputStream?.write(text.toByteArray(Charsets.UTF_8))
        } else {
            Log.w(TAG, "Writing text file $destinationUri failed. Empty text string text was provided.")
        }
    }


    /* Writes given bitmap as image file to storage */
    private fun writeImageFile(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 75) {
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            bitmap.compress(format, quality, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /* Checks the size of the collection folder */
    fun getCollectionFolderSize(context: Context): Int {
        val folder: File? = context.getExternalFilesDir(Keys.FOLDER_COLLECTION)
        val files = folder?.listFiles()
        if (folder != null && folder.exists() && folder.isDirectory) {
            return files?.size ?: -1
        } else {
            return -1
        }
    }


    /* Detects the MIME type of a file by inspecting its magic bytes (file signature). */
    fun detectMimeType(context: Context, uri: Uri): String {
        // 16 bytes is enough to identify all supported formats
        val header = readFileHeader(context, uri, 16) ?: return Keys.MIME_TYPE_UNSUPPORTED

        // IMAGE FORMATS
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) return Keys.MIME_TYPE_JPG // JPEG: FF D8 FF

        if (header.size >= 4 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) return Keys.MIME_TYPE_PNG // PNG: 89 50 4E 47 ("‰PNG")

        // AUDIO FORMATS
        if (header.size >= 4 &&
            header[0] == 0x4F.toByte() &&
            header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() &&
            header[3] == 0x53.toByte()
        ) return Keys.MIME_TYPE_OGG // OGG Vorbis/Opus: "OggS" (4F 67 67 53)

        if (header.size >= 3 &&
            header[0] == 0x49.toByte() &&
            header[1] == 0x44.toByte() &&
            header[2] == 0x33.toByte()
        ) return Keys.MIME_TYPE_MP3 // MP3 with ID3v2 tag: "ID3" (49 44 33)

        if (header.size >= 2 && header[0] == 0xFF.toByte()) {
            val b = header[1].toInt() and 0xFF
            // MP3 without ID3 tag (MPEG frame sync + Layer III)
            //   MPEG 1,   Layer III:  FB / FA
            //   MPEG 2,   Layer III:  F3 / F2
            //   MPEG 2.5, Layer III:  E3 / E2
            if (b == 0xFB || b == 0xFA ||
                b == 0xF3 || b == 0xF2 ||
                b == 0xE3 || b == 0xE2
            ) return Keys.MIME_TYPE_MP3
            // AAC in ADTS framing (12-bit sync 0xFFF, layer always 00)
            //   MPEG-4 AAC:  F1 / F0
            //   MPEG-2 AAC:  F9 / F8
            if (b == 0xF1 || b == 0xF0 ||
                b == 0xF9 || b == 0xF8
            ) return Keys.MIME_TYPE_AAC
        }

        if (header.size >= 8 &&
            header[4] == 0x66.toByte() &&
            header[5] == 0x74.toByte() &&
            header[6] == 0x79.toByte() &&
            header[7] == 0x70.toByte()
        ) return Keys.MIME_TYPE_MP4 // MP4 / M4A container: "ftyp" at byte offset 4

        // TEXT FORMATS
        if (header.size >= 5) {
            val offset = if (header.size >= 8 &&
                header[0] == 0xEF.toByte() &&
                header[1] == 0xBB.toByte() &&
                header[2] == 0xBF.toByte()
            ) 3 else 0

            if (header.size >= offset + 5 &&
                header[offset] == '<'.code.toByte() &&
                header[offset + 1] == '?'.code.toByte() &&
                header[offset + 2] == 'x'.code.toByte() &&
                header[offset + 3] == 'm'.code.toByte() &&
                header[offset + 4] == 'l'.code.toByte()
            ) return Keys.MIME_TYPE_XML // XML: "<?xml" with optional UTF-8 BOM (EF BB BF)
        }
        return Keys.MIME_TYPE_UNSUPPORTED
    }


    /* Reads the first bytes from a content URI */
    private fun readFileHeader(context: Context, uri: Uri, count: Int): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(count)
                val bytesRead = stream.read(buffer)
                if (bytesRead > 0) buffer.copyOf(bytesRead) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file header from URI: $uri")
            null
        }
    }


    /* Returns a nomedia file object */
    private fun getNoMediaFile(folder: File): File {
        return File(folder, ".nomedia")
    }


    /* Tries to parse feed URL string as URL */
    private fun isParsableAsUrl(feedUrl: String): Boolean {
        try {
            URL(feedUrl)
        } catch (e: Exception) {
            return false
        }
        return true
    }

}
