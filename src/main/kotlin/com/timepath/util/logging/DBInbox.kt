package com.timepath.util.logging


import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * @author TimePath
 */
public class DBInbox private() {
    class object {

        throws(javaClass<IOException>())
        public fun send(host: String, user: String, file: String, directory: String, message: String): String {
            val `in` = message.toByteArray()
            val baos = ByteArrayOutputStream(`in`.size())
            GZIPOutputStream(baos).use { gzip -> gzip.write(`in`) }
            val bytes = baos.toByteArray()
            val url = URL("http://$host/send/$user/$directory")
            val conn = url.openConnection() as HttpURLConnection
            conn.setDoOutput(true)
            conn.setRequestMethod("POST")
            conn.setRequestProperty("Connection", "keep-alive")
            conn.setRequestProperty("Content-Length", bytes.size().toString())
            val boundary = "**********"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            DataOutputStream(conn.getOutputStream()).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"$file\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            BufferedReader(InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)).use { br ->
                val sb = StringBuilder()
                br.forEachLine {
                    sb.append('\n').append(it)
                }
                return sb.substring(Math.min(1, sb.length()))
            }
        }
    }
}
