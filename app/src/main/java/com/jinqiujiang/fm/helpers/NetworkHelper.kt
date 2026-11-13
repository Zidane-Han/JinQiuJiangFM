/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.Uri
import android.util.Log
import com.jinqiujiang.fm.Keys
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val TAG: String = NetworkHelper::class.java.simpleName


    /* Main class variables */
    private const val CONNECT_TIMEOUT_MILLIS: Int = 15000
    private const val READ_TIMEOUT_MILLIS: Int = 15000
    private val ALLOWED_SCHEMES: List<String> = listOf("http", "https")


    /* Data class: holder for content type information */
    data class ContentType(var type: String = String(), var charset: String = String())


    /* Checks if the active network connection is over Wifi */
    fun isConnectedToWifi(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a Wifi connection is active
                result = capabilities.hasTransport(TRANSPORT_WIFI)
            }
        }
        return result
    }


    /* Checks if the active network connection is over Cellular */
    fun isConnectedToCellular(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a cellular connection is active
                result = capabilities.hasTransport(TRANSPORT_CELLULAR)
            }
        }
        return result
    }


    /* Checks if the active network connection is over VPN */
    fun isConnectedToVpn(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a VPN connection is active
                result = capabilities.hasTransport(TRANSPORT_VPN)
            }
        }
        return result
    }


    /* Checks if the active network connection is connected to any network */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        return activeNetwork != null
    }


    /* Detect content type (mime type) from given URL string  */
    fun detectContentType(urlString: String): ContentType {
        Log.v(TAG, "Determining content type - Thread: ${Thread.currentThread().name}")
        val contentType: ContentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
        val connection: HttpURLConnection? = createConnection(urlString)
        if (connection != null) {
            val contentTypeHeader: String = connection.contentType ?: String()
            Log.v(TAG, "Raw content type header: $contentTypeHeader")
            val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
            contentTypeHeaderParts.forEachIndexed { index, part ->
                if (index == 0 && part.isNotEmpty()) {
                    contentType.type = part.trim()
                } else if (part.contains("charset=")) {
                    contentType.charset = part.substringAfter("charset=").trim()
                }
            }
            connection.disconnect()
        }
        Log.i(TAG, "content type: ${contentType.type} | character set: ${contentType.charset}")
        return contentType
    }


    /* Checks if content type of remote file is supported */
    private fun checkContentTypeSupported(context: Context, uri: Uri): Boolean {
        val contentType: ContentType = detectContentType(uri.toString())
        val supportedContentTypes = Keys.MIME_TYPES_RSS + Keys.MIME_TYPES_AUDIO + Keys.MIME_TYPES_IMAGE
        return contentType.type in supportedContentTypes
    }


    /* Get redirected (real) URL */
    fun resolveRedirects(urlString: String): String {
        var redirectedURL: String = urlString
        val connection: HttpURLConnection? = createConnection(urlString) // createConnection() resolves redirects
        if (connection != null) {
            redirectedURL = connection.url.toString()
            connection.disconnect()
        }
        Log.i(TAG, "Resolved URL: $redirectedURL")
        return redirectedURL
    }


    /* Checks if a URL uses an allowed scheme (http or https) - cheap, no network I/O */
    fun isAllowedScheme(urlString: String): Boolean {
        val scheme: String = try {
            URL(urlString).protocol?.lowercase() ?: String()
        } catch (e: Exception) {
            String()
        }
        return scheme in ALLOWED_SCHEMES
    }


    /* Checks if a URL is safe to request: allowed scheme AND a host that does not resolve to a
     * private, link-local, loopback or wildcard address. Resolves DNS - call off the main thread.
     * DNS-rebinding / TOCTOU and exhaustive IPv6 coverage are intentionally out of scope. */
    fun isSafeRemoteUrl(urlString: String): Boolean {
        // reject bad scheme first (also rejects malformed URLs)
        if (!isAllowedScheme(urlString)) {
            Log.w(TAG, "Rejected URL with disallowed scheme: $urlString")
            return false
        }
        val host: String = try {
            URL(urlString).host ?: String()
        } catch (e: Exception) {
            String()
        }
        if (host.isEmpty()) {
            Log.w(TAG, "Rejected URL without host: $urlString")
            return false
        }
        return try {
            // reject if ANY resolved address is private/local
            val blocked: Boolean = InetAddress.getAllByName(host).any { it.isPrivateOrLocal() }
            if (blocked) Log.w(TAG, "Rejected URL resolving to a private/local address: $urlString")
            !blocked
        } catch (e: Exception) {
            Log.w(TAG, "Rejected URL with unresolvable host: $urlString")
            false
        }
    }


    /* Checks if an InetAddress points at a private, link-local, loopback or wildcard address */
    private fun InetAddress.isPrivateOrLocal(): Boolean {
        return isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isAnyLocalAddress
    }


    /* Creates a http connection from given url string */
    private fun createConnection(urlString: String, redirectCount: Int = 0): HttpURLConnection? {
        var connection: HttpURLConnection? = null

        // validate scheme + resolved host before opening (each redirect hop re-validates)
        if (!isSafeRemoteUrl(urlString)) {
            Log.e(TAG, "Refused to open connection to unsafe URL.")
            return null
        }

        try {
            // try to open connection and get status
            Log.i(TAG, "Opening http connection: $urlString")
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            // add user agent
            connection.setRequestProperty("User-Agent", Keys.USER_AGENT)
            val status = connection.responseCode

            // CHECK for non-HTTP_OK status
            if (status != HttpURLConnection.HTTP_OK) {
                // CHECK for redirect status
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER || status == Keys.HTTP_PERM_REDIR) {
                    val redirectUrl: String = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectCount < 5) {
                        Log.i(TAG, "Following redirect to $redirectUrl")
                        connection = createConnection(redirectUrl, redirectCount + 1)
                    } else {
                        connection = null
                        Log.e(TAG, "Too many redirects.")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unable to open http connection.")
            e.printStackTrace()
        }

        return connection
    }


}