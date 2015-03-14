package com.timepath.util.logging;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * @author TimePath
 */
public class DBInbox {

    private DBInbox() {
    }

    @NotNull
    public static String send(String host, String user, String file, String directory, @NotNull String message) throws IOException {
        @NotNull byte[] in = message.getBytes(StandardCharsets.UTF_8);
        @NotNull ByteArrayOutputStream baos = new ByteArrayOutputStream(in.length);
        try (@NotNull GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(in);
        }
        @NotNull byte[] bytes = baos.toByteArray();
        @NotNull URL url = new URL("http://" + host + "/send/" + user + "/" + directory);
        @NotNull HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        @NotNull String boundary = "**********";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (@NotNull DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"" + file + "\"\r\n");
            out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            out.write(bytes);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
        }
        try (@NotNull BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            @NotNull StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append('\n').append(line);
            }
            return sb.substring(Math.min(1, sb.length()));
        }
    }
}
