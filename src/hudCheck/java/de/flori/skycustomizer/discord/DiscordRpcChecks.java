package de.flori.skycustomizer.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flori.skycustomizer.config.SkyCustomizerConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class DiscordRpcChecks {
    private static int checks;
    private static final String ID = "1548045408259932221";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--live")) { live(); return; }
        run();
    }

    public static void run() throws Exception {
        var config = new SkyCustomizerConfig();
        config.discord.applicationId = "";
        config.discord.largeImageKey = "";
        config.discord.largeImageText = "";
        config.discord.details = "x".repeat(127) + "😀end";
        var normalize = SkyCustomizerConfig.class.getDeclaredMethod("normalize", SkyCustomizerConfig.class);
        normalize.setAccessible(true);
        normalize.invoke(null, config);
        check(config.discord.applicationId.equals(ID), "Empty Application ID uses the shared application");
        check(config.discord.largeImageKey.isEmpty() && config.discord.largeImageText.isEmpty(), "Saving preserves hidden artwork");
        check(config.discord.details.equals("x".repeat(127) + "😀"), "Text limit never splits an emoji");
        byte[] text = "Cyan ✦ Grüße 😀".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = DiscordIpcClient.encode(1, text);
        var stream = new ByteArrayInputStream(bytes);
        var fragmented = new DiscordIpcClient.Transport() {
            @Override public int read(byte[] target, int offset, int length) { return stream.read(target, offset, Math.min(2, length)); }
            @Override public void write(byte[] data) {}
            @Override public void close() {}
        };
        check(Arrays.equals(DiscordIpcClient.readFrame(fragmented).data(), text), "Fragmented UTF-8 payload survives");
        boolean eof = false;
        try { DiscordIpcClient.readFrame(fragmented); } catch (IOException expected) { eof = true; }
        check(eof, "EOF must fail rather than spin");
        for (int invalid : new int[]{-1, 1_048_577}) {
            byte[] header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(invalid).array();
            var bad = new Fake(); bad.bytes.add(header);
            boolean rejected = false;
            try { DiscordIpcClient.readFrame(bad); } catch (IOException expected) { rejected = true; }
            check(rejected, "Reject invalid frame length before allocation");
        }
        var endpoints = new LinkedBlockingQueue<Fake>();
        var opened = new AtomicInteger();
        var clock = new AtomicLong(TimeUnit.SECONDS.toNanos(100));
        Fake one = new Fake(); endpoints.add(one);
        try (var client = new DiscordIpcClient(() -> {
            opened.incrementAndGet();
            Fake next = endpoints.poll();
            if (next == null) throw new IOException("Discord absent");
            return next;
        }, clock::get)) {
            JsonObject activity = activity();
            client.update(ID, activity, 2, 3);
            byte[] hello = take(one.writes);
            check(ByteBuffer.wrap(hello).order(ByteOrder.LITTLE_ENDIAN).getInt() == 0, "Handshake opcode");
            check(json(hello).get("client_id").getAsString().equals(ID), "Exact shared Application ID");
            check(one.writes.poll(200, TimeUnit.MILLISECONDS) == null, "Do not publish before READY");
            check(!client.status().startsWith("Activity confirmed"), "Opening a pipe is not success");
            one.event(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}");
            JsonObject command = json(take(one.writes));
            check(command.get("cmd").getAsString().equals("SET_ACTIVITY"), "Publish after READY");
            check(command.getAsJsonObject("args").get("pid").getAsLong() == ProcessHandle.current().pid(), "Actual process ID");
            check(!client.status().startsWith("Activity confirmed"), "Wait for Discord acknowledgement");
            one.event(1, "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"unrelated\",\"evt\":null,\"data\":{}}");
            one.event(1, ack(command));
            await(() -> client.status().startsWith("Activity confirmed"), "Handle null evt acknowledgement");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(3));
            client.update(ID, activity, 2, 3);
            check(one.writes.poll(250, TimeUnit.MILLISECONDS) == null, "Unchanged activity is not spammed");
            one.event(3, "{\"probe\":1}");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(30));
            byte[] ping = take(one.writes);
            check(ByteBuffer.wrap(ping).order(ByteOrder.LITTLE_ENDIAN).getInt() == 3, "Heartbeat detects idle disconnects");
            byte[] pong = take(one.writes);
            check(ByteBuffer.wrap(pong).order(ByteOrder.LITTLE_ENDIAN).getInt() == 4, "Respond to PING");
            check(json(pong).get("probe").getAsInt() == 1, "Echo the PING payload");
            one.event(4, "{}");
            activity.addProperty("state", "Changed"); client.update(ID, activity, 2, 3);
            JsonObject changed = json(take(one.writes));
            one.event(1, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":\"ERROR\",\"nonce\":\"" + changed.get("nonce").getAsString()
                    + "\",\"data\":{\"code\":4000,\"message\":\"Invalid asset\"}}");
            await(() -> client.status().contains("4000: Invalid asset"), "Show exact Discord error");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(20));
            check(one.writes.poll(250, TimeUnit.MILLISECONDS) == null, "Rejected payload is not spammed");
            Fake two = new Fake(); endpoints.add(two); client.reconnect();
            JsonObject clear = json(take(one.writes));
            check(clear.getAsJsonObject("args").get("activity").isJsonNull(), "Clear old presence before reconnect");
            take(two.writes);
            two.event(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}");
            two.event(1, ack(json(take(two.writes))));
            await(() -> client.status().startsWith("Activity confirmed"), "Reconnect sends again");
            client.disable("Disabled");
            check(json(take(two.writes)).getAsJsonObject("args").get("activity").isJsonNull(), "Disabling clears activity");
            await(() -> client.status().equals("Disabled"), "Disabled status");
            client.update(ID, activity, 2, 3);
            await(() -> client.status().startsWith("Discord not found"), "Missing Discord is visible");
            int attempts = opened.get();
            Thread.sleep(250); check(opened.get() == attempts, "Back off while Discord is closed");
            Fake three = new Fake(); endpoints.add(three); clock.addAndGet(TimeUnit.SECONDS.toNanos(4));
            take(three.writes);
            check(!client.status().startsWith("Activity confirmed"), "Reconnect still requires READY");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(9));
            await(() -> client.status().contains("handshake timed out"), "Handshake has a deadline");
            Fake four = new Fake(); endpoints.add(four); clock.addAndGet(TimeUnit.SECONDS.toNanos(4));
            take(four.writes);
            four.event(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}");
            take(four.writes);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(9));
            await(() -> client.status().contains("did not confirm activity"), "An open pipe without an acknowledgement is not success");
            Fake five = new Fake(); endpoints.add(five); clock.addAndGet(TimeUnit.SECONDS.toNanos(4));
            take(five.writes);
            five.event(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}");
            five.event(1, ack(json(take(five.writes))));
            await(() -> client.status().startsWith("Activity confirmed"), "Recover after acknowledgement timeout");
            five.close();
            clock.addAndGet(TimeUnit.SECONDS.toNanos(31));
            await(() -> client.status().contains("disconnected"), "Idle EOF triggers automatic reconnect");
        }
        System.out.println("Discord RPC: " + checks + " checks passed.");
    }

    private static JsonObject activity() {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", "SkyCustomizer connection test");
        activity.addProperty("state", "Local RPC verification");
        return activity;
    }

    private static void live() throws Exception {
        try (var client = new DiscordIpcClient()) {
            JsonObject payload = activity();
            JsonObject assets = new JsonObject(); assets.addProperty("large_image", "skycustomizer_cover");
            payload.add("assets", assets);
            client.update(ID, payload, 5, 10);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            String last = "";
            while (System.nanoTime() < deadline) {
                String status = client.status();
                if (!status.equals(last)) { System.out.println("Discord RPC live: " + status); last = status; }
                if (status.startsWith("Activity confirmed")) {
                    client.disable("Test complete; presence cleared");
                    await(() -> client.status().equals("Test complete; presence cleared"), "Live cleanup");
                    System.out.println("DISCORD_RPC_LIVE_PASSED"); return;
                }
                if (status.startsWith("Discord error")) throw new AssertionError(status);
                Thread.sleep(100);
            }
            throw new AssertionError("Live RPC failed: " + client.status());
        }
    }

    private static String ack(JsonObject command) {
        return "{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"nonce\":\"" + command.get("nonce").getAsString() + "\",\"data\":{}}";
    }
    private static JsonObject json(byte[] frame) {
        return JsonParser.parseString(new String(frame, 8, frame.length - 8, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static byte[] take(BlockingQueue<byte[]> queue) throws Exception {
        byte[] result = queue.poll(4, TimeUnit.SECONDS);
        if (result == null) throw new AssertionError("Missing IPC frame");
        return result;
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        check(condition.getAsBoolean(), message);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static final class Fake implements DiscordIpcClient.Transport {
        final BlockingQueue<byte[]> bytes = new LinkedBlockingQueue<>(), writes = new LinkedBlockingQueue<>();
        private ByteArrayInputStream current = new ByteArrayInputStream(new byte[0]);
        private volatile boolean closed;
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            while (current.available() == 0) {
                if (closed) return -1;
                try { current = new ByteArrayInputStream(bytes.take()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            return current.read(target, offset, Math.min(3, length));
        }
        @Override public void write(byte[] data) { writes.add(data); }
        @Override public void close() { closed = true; bytes.add(new byte[0]); }
        void event(int opcode, String json) { bytes.add(DiscordIpcClient.encode(opcode, json.getBytes(StandardCharsets.UTF_8))); }
    }
}
