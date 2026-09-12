package de.flori.skycustomizer.discord;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class DiscordIpcClient implements AutoCloseable {
    interface Transport extends Closeable {
        int read(byte[] bytes, int offset, int length) throws IOException;
        void write(byte[] bytes) throws IOException;
    }
    interface Connector { Transport open() throws IOException; }
    record Frame(int opcode, byte[] data) {}
    private record Request(String applicationId, String activity, int interval, int retry) {}
    private final Connector connector;
    private final LongSupplier clock;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("SkyCustomizer Discord").factory());
    private final AtomicLong revision = new AtomicLong();
    private volatile Request request;
    private volatile String status = "Disabled";
    private volatile String idleStatus = "Disabled";
    private volatile boolean closed;
    private Session session;
    private long seenRevision, nextConnect, nextSend, deadline, nextPing;
    private boolean waitingForPong;
    private String lastSent = "", pendingNonce = "", failedPayload = "";

    public DiscordIpcClient() { this(DiscordIpcClient::openLocal, System::nanoTime); }

    DiscordIpcClient(Connector connector, LongSupplier clock) {
        this.connector = connector;
        this.clock = clock;
        worker.scheduleWithFixedDelay(this::stepSafely, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void update(String applicationId, JsonObject activity, int interval, int retry) {
        request = new Request(applicationId, activity.toString(), Math.clamp(interval, 2, 120), Math.clamp(retry, 3, 120));
    }

    public void disable(String reason) {
        idleStatus = reason;
        request = null;
    }

    public void reconnect() { revision.incrementAndGet(); }
    public String status() { return status; }

    private void stepSafely() {
        try { step(); }
        catch (Exception error) {
            disconnect();
            status = "Discord disconnected; retrying";
            Request current = request;
            nextConnect = clock.getAsLong() + TimeUnit.SECONDS.toNanos(current == null ? 10 : current.retry);
        }
    }

    private void step() throws IOException {
        if (closed) return;
        long now = clock.getAsLong();
        Request current = request;
        if (current == null) {
            disconnect();
            nextConnect = 0;
            status = idleStatus;
            return;
        }
        long version = revision.get();
        if (seenRevision != version || session != null && !session.applicationId.equals(current.applicationId)) {
            seenRevision = version;
            disconnect();
            nextConnect = 0;
        }
        if (session == null) {
            if (now < nextConnect) return;
            nextConnect = now + TimeUnit.SECONDS.toNanos(current.retry);
            status = "Connecting to Discord";
            try { session = new Session(current.applicationId, connector.open()); }
            catch (IOException error) { status = "Discord not found; open the desktop app"; return; }
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", current.applicationId);
            session.send(0, handshake);
            deadline = now + TimeUnit.SECONDS.toNanos(8);
            nextPing = now + TimeUnit.SECONDS.toNanos(30);
        }
        Frame frame;
        while (session != null && (frame = session.incoming.poll()) != null) receive(frame, current, now);
        if (session == null) return;
        if (session.failure != null) throw session.failure;
        if ((!session.ready || !pendingNonce.isEmpty() || waitingForPong) && now >= deadline) {
            boolean ready = session.ready;
            boolean heartbeat = waitingForPong;
            disconnect();
            status = heartbeat ? "Discord stopped responding; retrying"
                    : ready ? "Discord did not confirm activity; retrying" : "Discord handshake timed out; retrying";
            nextConnect = now + TimeUnit.SECONDS.toNanos(current.retry);
            return;
        }
        if (session.ready && pendingNonce.isEmpty() && !waitingForPong && now >= nextSend
                && !current.activity.equals(lastSent) && !current.activity.equals(failedPayload)) {
            pendingNonce = UUID.randomUUID().toString();
            session.send(1, activityCommand(JsonParser.parseString(current.activity).getAsJsonObject(), pendingNonce));
            lastSent = current.activity;
            deadline = now + TimeUnit.SECONDS.toNanos(8);
            nextSend = now + TimeUnit.SECONDS.toNanos(current.interval);
            nextPing = now + TimeUnit.SECONDS.toNanos(30);
            status = "Connected; waiting for activity confirmation";
        }
        if (session.ready && pendingNonce.isEmpty() && !waitingForPong && now >= nextPing) {
            session.send(3, new JsonObject());
            waitingForPong = true;
            deadline = now + TimeUnit.SECONDS.toNanos(8);
            nextPing = now + TimeUnit.SECONDS.toNanos(30);
        }
        if (!session.ready || !pendingNonce.isEmpty() || waitingForPong) session.requestRead();
    }

    private void receive(Frame frame, Request current, long now) throws IOException {
        if (frame.opcode == 3) { session.transport.write(encode(4, frame.data)); return; }
        if (frame.opcode == 4) { waitingForPong = false; return; }
        JsonObject data = JsonParser.parseString(new String(frame.data, StandardCharsets.UTF_8)).getAsJsonObject();
        if (frame.opcode == 2) {
            String error = errorMessage(data);
            disconnect();
            status = error;
            nextConnect = now + TimeUnit.SECONDS.toNanos(current.retry);
            return;
        }
        if (frame.opcode != 1) return;
        String event = text(data, "evt");
        if (event.equals("READY") && text(data, "cmd").equals("DISPATCH")) {
            session.ready = true;
            status = "Connected";
        } else if (event.equals("ERROR")) {
            String error = errorMessage(data.has("data") && data.get("data").isJsonObject() ? data.getAsJsonObject("data") : data);
            if (!session.ready) {
                disconnect();
                nextConnect = now + TimeUnit.SECONDS.toNanos(current.retry);
            } else if (!pendingNonce.isEmpty() && text(data, "nonce").equals(pendingNonce)) {
                failedPayload = lastSent;
                pendingNonce = "";
            }
            status = error;
        } else if (text(data, "cmd").equals("SET_ACTIVITY") && !pendingNonce.isEmpty()
                && text(data, "nonce").equals(pendingNonce)) {
            pendingNonce = "";
            failedPayload = "";
            status = "Activity confirmed by Discord";
        }
    }

    private static String errorMessage(JsonObject data) {
        String message = text(data, "message").replaceAll("[\\r\\n]", " ");
        return "Discord error " + text(data, "code") + ": " + message.substring(0, Math.min(150, message.length()));
    }

    private static String text(JsonObject data, String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : "";
    }

    private static JsonObject activityCommand(JsonObject activity, String nonce) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity == null ? JsonNull.INSTANCE : activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.addProperty("nonce", nonce);
        command.add("args", args);
        return command;
    }

    private void disconnect() {
        Session old = session;
        session = null;
        if (old != null) {
            try { if (old.ready && old.failure == null && !old.reading.get()) old.send(1, activityCommand(null, UUID.randomUUID().toString())); }
            catch (IOException ignored) {}
            finally { old.close(); }
        }
        lastSent = "";
        pendingNonce = "";
        failedPayload = "";
        waitingForPong = false;
        nextSend = 0;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        worker.execute(this::disconnect);
        worker.shutdown();
    }

    private static final class Session {
        final String applicationId;
        final Transport transport;
        final ArrayBlockingQueue<Frame> incoming = new ArrayBlockingQueue<>(64);
        final Semaphore reads = new Semaphore(0);
        final AtomicBoolean reading = new AtomicBoolean();
        volatile IOException failure;
        volatile boolean closed;
        boolean ready;
        Session(String applicationId, Transport transport) {
            this.applicationId = applicationId;
            this.transport = transport;
            Thread.ofPlatform().daemon().name("SkyCustomizer Discord reader").start(() -> {
                try {
                    while (!closed) {
                        reads.acquire();
                        if (closed) return;
                        Frame frame = readFrame(transport);
                        if (!incoming.offer(frame)) throw new IOException("IPC receive queue full");
                        reading.set(false);
                    }
                } catch (Exception error) { failure = new IOException("Discord IPC closed", error); }
            });
        }
        void requestRead() { if (incoming.isEmpty() && reading.compareAndSet(false, true)) reads.release(); }
        void send(int opcode, JsonObject data) throws IOException {
            transport.write(encode(opcode, data.toString().getBytes(StandardCharsets.UTF_8)));
        }
        void close() { closed = true; reads.release(); try { transport.close(); } catch (IOException ignored) {} }
    }

    static byte[] encode(int opcode, byte[] body) {
        return ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode).putInt(body.length).put(body).array();
    }

    static Frame readFrame(Transport transport) throws IOException {
        byte[] header = readFully(transport, 8);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = buffer.getInt(), size = buffer.getInt();
        if (opcode < 0 || opcode > 4 || size < 0 || size > 1_048_576) throw new IOException("Invalid Discord IPC frame");
        return new Frame(opcode, readFully(transport, size));
    }

    private static byte[] readFully(Transport transport, int size) throws IOException {
        byte[] data = new byte[size];
        for (int offset = 0; offset < size;) {
            int count = transport.read(data, offset, size - offset);
            if (count < 0) throw new EOFException("Discord disconnected");
            if (count == 0) throw new IOException("Empty Discord IPC read");
            offset += count;
        }
        return data;
    }

    private static Transport openLocal() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            for (int index = 0; index < 10; index++) {
                try {
                    var pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + index, "rw");
                    return new Transport() {
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException { return pipe.read(bytes, offset, length); }
                        @Override public void write(byte[] bytes) throws IOException { pipe.write(bytes); }
                        @Override public void close() throws IOException { pipe.close(); }
                    };
                } catch (IOException ignored) {}
            }
        } else {
            var roots = new LinkedHashSet<String>();
            for (String key : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String value = System.getenv(key);
                if (value != null && !value.isBlank()) roots.add(value);
            }
            roots.add("/tmp");
            for (String root : roots) for (int index = 0; index < 10; index++) {
                SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                try {
                    channel.connect(UnixDomainSocketAddress.of(Path.of(root, "discord-ipc-" + index)));
                    return new Transport() {
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException { return channel.read(ByteBuffer.wrap(bytes, offset, length)); }
                        @Override public void write(byte[] bytes) throws IOException {
                            ByteBuffer buffer = ByteBuffer.wrap(bytes);
                            while (buffer.hasRemaining()) channel.write(buffer);
                        }
                        @Override public void close() throws IOException { channel.close(); }
                    };
                } catch (IOException error) { channel.close(); }
            }
        }
        throw new IOException("Discord desktop IPC not found");
    }
}
