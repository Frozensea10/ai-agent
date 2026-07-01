package com.agent.redis;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RedisClientTest {
    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", 6379);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            send(out, "*1\r\n$4\r\nPING\r\n");
            System.out.println("PING: " + readResponse(in));

            send(out, "*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$5\r\nhello\r\n");
            System.out.println("SET: " + readResponse(in));

            send(out, "*2\r\n$3\r\nGET\r\n$5\r\nmykey\r\n");
            System.out.println("GET: " + readResponse(in));

            send(out, "*2\r\n$3\r\nDEL\r\n$5\r\nmykey\r\n");
            System.out.println("DEL: " + readResponse(in));

            send(out, "*2\r\n$3\r\nGET\r\n$5\r\nmykey\r\n");
            System.out.println("GET after DEL: " + readResponse(in));
        }
    }

    private static void send(OutputStream out, String cmd) throws IOException {
        out.write(cmd.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readResponse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            sb.append((char) b);
            if (sb.length() >= 2 && sb.substring(sb.length() - 2).equals("\r\n")) {
                break;
            }
        }
        return sb.toString().trim();
    }
}
