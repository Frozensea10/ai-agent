package com.agent.redis;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmbeddedRedisServer {
    private static final int PORT = 6379;
    private static final Map<String, String> store = new HashMap<>();
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println("[EmbeddedRedis] Starting embedded Redis server on port " + PORT + "...");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[EmbeddedRedis] Server started successfully. Listening on port " + PORT);
            while (running) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[EmbeddedRedis] Server error: " + e.getMessage());
        } finally {
            executor.shutdown();
            System.out.println("[EmbeddedRedis] Server stopped.");
        }
    }

    private static void handleClient(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            while (running && socket.isConnected() && !socket.isClosed()) {
                Object command = parseRESP(in);
                if (command == null) break;
                if (command instanceof String[] arr) {
                    String response = executeCommand(arr);
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } else if (command instanceof String simple) {
                    if (simple.isEmpty()) continue;
                    String[] parts = simple.trim().split("\\s+");
                    String response = executeCommand(parts);
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("[EmbeddedRedis] Client disconnected: " + e.getMessage());
        }
    }

    private static Object parseRESP(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) return null;
        if (firstByte == '*') {
            int count = readInteger(in);
            String[] array = new String[count];
            for (int i = 0; i < count; i++) {
                int b = in.read();
                if (b == '$') {
                    int len = readInteger(in);
                    byte[] data = new byte[len];
                    int read = in.read(data);
                    if (read != len) {
                        throw new IOException("Incomplete bulk string");
                    }
                    array[i] = new String(data, StandardCharsets.UTF_8);
                    in.read();
                    in.read();
                }
            }
            return array;
        } else if (firstByte == '+') {
            return readLine(in);
        } else if (firstByte == '-') {
            return readLine(in);
        } else if (firstByte == ':') {
            return readLine(in);
        } else if (firstByte == '$') {
            int len = readInteger(in);
            if (len == -1) return null;
            byte[] data = new byte[len];
            in.read(data);
            in.read();
            in.read();
            return new String(data, StandardCharsets.UTF_8);
        } else {
            String line = ((char) firstByte) + readLine(in);
            return line;
        }
    }

    private static int readInteger(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1 && b != '\r') {
            sb.append((char) b);
        }
        in.read();
        return Integer.parseInt(sb.toString());
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1 && b != '\r') {
            sb.append((char) b);
        }
        in.read();
        return sb.toString();
    }

    private static String executeCommand(String[] parts) {
        if (parts.length == 0) return "-ERR unknown command\r\n";
        String cmd = parts[0].toUpperCase();
        switch (cmd) {
            case "PING":
                return parts.length > 1 ? "+" + parts[1] + "\r\n" : "+PONG\r\n";
            case "GET":
                if (parts.length < 2) return "-ERR wrong number of arguments for 'get' command\r\n";
                String value = store.get(parts[1]);
                return value == null ? "$-1\r\n" : "+" + value + "\r\n";
            case "SET":
                if (parts.length < 3) return "-ERR wrong number of arguments for 'set' command\r\n";
                store.put(parts[1], parts[2]);
                return "+OK\r\n";
            case "DEL":
                if (parts.length < 2) return "-ERR wrong number of arguments for 'del' command\r\n";
                int removed = 0;
                for (int i = 1; i < parts.length; i++) {
                    if (store.remove(parts[i]) != null) removed++;
                }
                return ":" + removed + "\r\n";
            case "EXISTS":
                if (parts.length < 2) return "-ERR wrong number of arguments for 'exists' command\r\n";
                int exists = store.containsKey(parts[1]) ? 1 : 0;
                return ":" + exists + "\r\n";
            case "KEYS":
                if (parts.length < 2) return "-ERR wrong number of arguments for 'keys' command\r\n";
                String pattern = parts[1];
                StringBuilder keysBuilder = new StringBuilder();
                int keyCount = 0;
                for (String key : store.keySet()) {
                    if (matchPattern(key, pattern)) {
                        keyCount++;
                    }
                }
                keysBuilder.append("*").append(keyCount).append("\r\n");
                for (String key : store.keySet()) {
                    if (matchPattern(key, pattern)) {
                        keysBuilder.append("$").append(key.length()).append("\r\n").append(key).append("\r\n");
                    }
                }
                return keysBuilder.toString();
            case "FLUSHALL":
                store.clear();
                return "+OK\r\n";
            case "DBSIZE":
                return ":" + store.size() + "\r\n";
            case "COMMAND":
                return "+OK\r\n";
            case "QUIT":
                return "+OK\r\n";
            default:
                return "-ERR unknown command '" + cmd + "'\r\n";
        }
    }

    private static boolean matchPattern(String key, String pattern) {
        if (pattern.equals("*")) return true;
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            String middle = pattern.substring(1, pattern.length() - 1);
            return key.contains(middle);
        }
        if (pattern.endsWith("*")) {
            return key.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return key.endsWith(pattern.substring(1));
        }
        return key.equals(pattern);
    }
}
