package com.timepath.logging;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * @author TimePath
 */
public class DBInbox {

    private DBInbox() {}

    public static String send(String user, String file, String directory, String message) throws IOException {
        byte[] in = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(in.length);
        try(GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(in);
        }
        byte[] bytes = baos.toByteArray();
        URL url = new URL("http://dbinbox.com/send/" + user + "/" + directory);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        String boundary = "**********";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try(DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"files[]\"; filename=\"" + file + "\"\r\n");
            out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            out.write(bytes);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
        }
        try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(0);
            for(String line; ( line = br.readLine() ) != null; ) {
                sb.append('\n').append(line);
            }
            return sb.toString();
        }
    }
}
