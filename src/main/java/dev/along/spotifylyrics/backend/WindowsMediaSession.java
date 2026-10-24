package dev.along.spotifylyrics.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.time.Instant;

/**
 * Minimal Windows GSMTC reader using Java 25 Panama FFM, without JNA or a helper process.
 * Use this instance from the same thread that called open(), and close it on that thread.
 */
public final class WindowsMediaSession implements AutoCloseable {
    private static final String CLASS_NAME =
            "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final String FACTORY_IID = "2050c4ee-11a0-57de-aed7-c97c70338245";
    private static final String ASYNC_INFO_IID = "00000036-0000-0000-C000-000000000046";

    private final Thread ownerThread;
    private final Arena arena;
    private final Linker linker;
    private final SymbolLookup combase;
    private final MethodHandle roUninitialize;
    private final MethodHandle createString;
    private final MethodHandle getStringRawBuffer;
    private final MethodHandle deleteString;
    private final MethodHandle getFactory;
    private boolean winRtInitialized;
    private boolean closed;
    private MemorySegment factory = MemorySegment.NULL;
    private MemorySegment manager = MemorySegment.NULL;
    private MemorySegment session = MemorySegment.NULL;

    private WindowsMediaSession() throws Throwable {
        ownerThread = Thread.currentThread();
        arena = Arena.ofShared();
        linker = Linker.nativeLinker();
        combase = SymbolLookup.libraryLookup("combase.dll", arena);

        MethodHandle roInitialize = linker.downcallHandle(
                combase.find("RoInitialize").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        roUninitialize = linker.downcallHandle(
                combase.find("RoUninitialize").orElseThrow(),
                FunctionDescriptor.ofVoid());
        createString = linker.downcallHandle(
                combase.find("WindowsCreateString").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        getStringRawBuffer = linker.downcallHandle(
                combase.find("WindowsGetStringRawBuffer").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        deleteString = linker.downcallHandle(
                combase.find("WindowsDeleteString").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        getFactory = linker.downcallHandle(
                combase.find("RoGetActivationFactory").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        try {
            int initHr = (int) roInitialize.invokeExact(1);

            if (initHr == 0 || initHr == 1) {
                // S_OK or S_FALSE: this call acquired an initialization count.
                winRtInitialized = true;
            } else if (initHr == 0x80010106) {
                // RPC_E_CHANGED_MODE: COM was initialized with another apartment model.
                // Do not call RoUninitialize() for this failed initialization.
                winRtInitialized = false;
            } else {
                throw hresult("RoInitialize", initHr);
            }
            if (initHr < 0) throw hresult("RoInitialize", initHr);
            winRtInitialized = true;

            acquireFactory();
            acquireManagerAndSession();
        } catch (Throwable t) {
            try { close(); } catch (Throwable cleanup) { t.addSuppressed(cleanup); }
            throw t;
        }
    }

    public static WindowsMediaSession open() throws Throwable {
        return new WindowsMediaSession();
    }

    public String getTitle() throws Throwable { return getMediaString(6); }
    public String getAlbum() throws Throwable { return getMediaString(10); }
    /** GSMTC media-properties Artist getter (IInspectable slots 0-5; Title=6, Subtitle=7, Artist=8). */
    public String getArtist() throws Throwable { return getMediaString(9); }


    public long getPositionMs(boolean isPlaying) throws Throwable {
        MemorySegment timeline = getTimeline();

        try {
            long positionTicks = readInt64Getter(timeline, 10, "Position");
            long updatedTicks = readInt64Getter(timeline, 11, "LastUpdatedTime");
            long startTicks = readInt64Getter(timeline, 6, "StartTime");
            long endTicks = readInt64Getter(timeline, 7, "EndTime");

            long position = positionTicks / 10_000L;
            long trackDuration = Math.max(
                    0L, (endTicks - startTicks) / 10_000L
            );

            // Windows DateTime uses 100-nanosecond ticks since 1601-01-01.
            if (isPlaying && updatedTicks > 0) {
                long updatedEpochMs =
                        (updatedTicks - 116444736000000000L) / 10_000L;

                long ageMs = System.currentTimeMillis() - updatedEpochMs;

                // Avoid applying an invalid or stale timestamp correction.
                if (ageMs >= 0 && ageMs <= 5000) {
                    position += ageMs;
                }
            }

            if (trackDuration > 0) {
                position = Math.min(position, trackDuration);
            }

            return Math.max(0L, position);
        } finally {
            releaseComObject(timeline);
        }
    }


    public long getDurationMs() throws Throwable {
        MemorySegment timeline = getTimeline();
        try {
            long start = readInt64Getter(timeline, 6, "StartTime");
            long end = readInt64Getter(timeline, 7, "EndTime");
            return Math.max(0L, (end - start) / 10_000L);
        } finally { releaseComObject(timeline); }
    }

    public boolean isPlaying() throws Throwable {
        checkUsable();

        MemorySegment playbackInfo =
                callObjectGetter(session, 9, "GetPlaybackInfo");

        try {


            int status = readInt32Getter(
                    playbackInfo, 7, "PlaybackStatus"
            );



            return status == 4;
        } finally {
            releaseComObject(playbackInfo);
        }
    }

    private void acquireFactory() throws Throwable {
        try (Arena data = Arena.ofConfined()) {
            MemorySegment utf16 = data.allocateFrom(CLASS_NAME, StandardCharsets.UTF_16LE);
            MemorySegment hstringOut = data.allocate(ValueLayout.ADDRESS);
            int createHr = (int) createString.invokeExact(utf16, CLASS_NAME.length(), hstringOut);
            if (createHr < 0) throw hresult("WindowsCreateString", createHr);
            MemorySegment hstring = hstringOut.get(ValueLayout.ADDRESS, 0);
            try {
                MemorySegment iid = writeGuid(data, UUID.fromString(FACTORY_IID));
                MemorySegment out = data.allocate(ValueLayout.ADDRESS);
                int hr = (int) getFactory.invokeExact(hstring, iid, out);
                if (hr < 0) throw hresult("RoGetActivationFactory", hr);
                factory = out.get(ValueLayout.ADDRESS, 0);
                requirePointer(factory, "activation factory");
            } finally {
                deleteString.invokeExact(hstring);
            }
        }
    }

    private void acquireManagerAndSession() throws Throwable {
        MemorySegment asyncOperation = callObjectGetter(factory, 6, "RequestAsync");
        try {
            manager = (MemorySegment) awaitAsyncResult(asyncOperation);
            requirePointer(manager, "media manager");
        } finally { releaseComObject(asyncOperation); }

        session = callObjectGetterAllowNull(manager, 6, "GetCurrentSession");
        // A null session is valid when no app is publishing a media session.
    }

    private Object awaitAsyncResult(MemorySegment operation) throws Throwable {
        MemorySegment asyncInfo = queryInterface(operation, UUID.fromString(ASYNC_INFO_IID));
        try {
            long deadline = System.nanoTime() + 10_000_000_000L;
            int status;
            do {
                status = readInt32Getter(asyncInfo, 7, "IAsyncInfo.Status");
                if (status == 0) Thread.sleep(50);
            } while (status == 0 && System.nanoTime() < deadline);
            if (status != 1) throw new IllegalStateException("Async operation did not complete successfully; status=" + status);
        } finally { releaseComObject(asyncInfo); }

        MethodHandle getResults = comMethod(operation, 8,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
        out.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        int hr = (int) getResults.invokeExact(operation, out);
        if (hr < 0) throw hresult("IAsyncOperation.GetResults", hr);
        return out.get(ValueLayout.ADDRESS, 0);
    }

    private String getMediaString(int slot) throws Throwable {
        checkUsable();
        if (session.address() == 0) return "";
        MemorySegment operation = callObjectGetter(session, 7, "TryGetMediaPropertiesAsync");
        MemorySegment properties = MemorySegment.NULL;
        try {
            properties = (MemorySegment) awaitAsyncResult(operation);
            requirePointer(properties, "media properties");
            MemorySegment hstringOut = arena.allocate(ValueLayout.ADDRESS);
            hstringOut.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            MethodHandle getter = comMethod(properties, slot,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            int hr = (int) getter.invokeExact(properties, hstringOut);
            if (hr < 0) throw hresult(slot == 6 ? "Title" : "Artist", hr);
            MemorySegment hstring = hstringOut.get(ValueLayout.ADDRESS, 0);
            if (hstring.address() == 0) return "";
            try {
                MemorySegment lengthOut = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment chars = (MemorySegment) getStringRawBuffer.invokeExact(hstring, lengthOut);
                int length = lengthOut.get(ValueLayout.JAVA_INT, 0);
                if (length <= 0) return "";
                byte[] bytes = chars.reinterpret((long) length * 2).toArray(ValueLayout.JAVA_BYTE);
                return new String(bytes, StandardCharsets.UTF_16LE);
            } finally { deleteString.invokeExact(hstring); }
        } finally {
            if (properties.address() != 0) releaseComObject(properties);
            releaseComObject(operation);
        }
    }

    private MemorySegment getTimeline() throws Throwable {
        checkUsable();
        if (session.address() == 0) throw new IllegalStateException("No current media session");
        return callObjectGetter(session, 8, "GetTimelineProperties");
    }

    private long readInt64Getter(MemorySegment object, int slot, String name) throws Throwable {
        MethodHandle getter = comMethod(object, slot,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
        int hr = (int) getter.invokeExact(object, out);
        if (hr < 0) throw hresult(name, hr);
        return out.get(ValueLayout.JAVA_LONG, 0);
    }

    private int readInt32Getter(MemorySegment object, int slot, String name) throws Throwable {
        MethodHandle getter = comMethod(object, slot,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
        int hr = (int) getter.invokeExact(object, out);
        if (hr < 0) throw hresult(name, hr);
        return out.get(ValueLayout.JAVA_INT, 0);
    }

    private MemorySegment callObjectGetter(MemorySegment object, int slot, String name) throws Throwable {
        MemorySegment result = callObjectGetterAllowNull(object, slot, name);
        requirePointer(result, name + " result");
        return result;
    }

    private MemorySegment callObjectGetterAllowNull(MemorySegment object, int slot, String name) throws Throwable {
        MethodHandle getter = comMethod(object, slot,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
        out.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        int hr = (int) getter.invokeExact(object, out);
        if (hr < 0) throw hresult(name, hr);
        return out.get(ValueLayout.ADDRESS, 0);
    }

    private MemorySegment queryInterface(MemorySegment object, UUID iidValue) throws Throwable {
        MemorySegment iid = writeGuid(arena, iidValue);
        MethodHandle qi = comMethod(object, 0,
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
        out.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        int hr = (int) qi.invokeExact(object, iid, out);
        if (hr < 0) throw hresult("QueryInterface", hr);
        MemorySegment result = out.get(ValueLayout.ADDRESS, 0);
        requirePointer(result, "QueryInterface result");
        return result;
    }

    private static MemorySegment writeGuid(Arena arena, UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        MemorySegment guid = arena.allocate(16, 4);
        guid.set(ValueLayout.JAVA_INT, 0, (int) (most >>> 32));
        guid.set(ValueLayout.JAVA_SHORT, 4, (short) (most >>> 16));
        guid.set(ValueLayout.JAVA_SHORT, 6, (short) most);
        for (int i = 0; i < 8; i++) {
            guid.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) (least >>> (56 - i * 8)));
        }
        return guid;
    }

    private MethodHandle comMethod(MemorySegment object, int slot, FunctionDescriptor descriptor) {
        MemorySegment vtable = object.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
        MemorySegment methods = vtable.reinterpret((slot + 1L) * ValueLayout.ADDRESS.byteSize());
        MemorySegment function = methods.get(ValueLayout.ADDRESS, (long) slot * ValueLayout.ADDRESS.byteSize());
        return linker.downcallHandle(function, descriptor);
    }

    private void releaseComObject(MemorySegment object) throws Throwable {
        if (object == null || object.address() == 0) return;
        MemorySegment vtable = object.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
        MemorySegment methods = vtable.reinterpret(3L * ValueLayout.ADDRESS.byteSize());
        MemorySegment releaseFunction = methods.get(ValueLayout.ADDRESS, 2L * ValueLayout.ADDRESS.byteSize());
        MethodHandle release = linker.downcallHandle(releaseFunction,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        int remainingReferences = (int) release.invokeExact(object);
    }

    private void checkUsable() {
        if (closed) throw new IllegalStateException("WindowsMediaSession is closed");
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Use WindowsMediaSession on the same thread that opened it");
        }
    }

    private static void requirePointer(MemorySegment pointer, String name) {
        if (pointer == null || pointer.address() == 0) throw new IllegalStateException("Windows returned a null " + name);
    }

    private static IllegalStateException hresult(String operation, int hr) {
        return new IllegalStateException(operation + " failed: 0x" + Integer.toHexString(hr));
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Close WindowsMediaSession on the same thread that opened it");
        }
        closed = true;
        Throwable failure = null;
        try { releaseComObject(session); } catch (Throwable t) { failure = t; }
        try { releaseComObject(manager); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
        try { releaseComObject(factory); } catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
        try { if (winRtInitialized) roUninitialize.invokeExact(); }
        catch (Throwable t) { if (failure == null) failure = t; else failure.addSuppressed(t); }
        finally { arena.close(); }
        if (failure != null) {
            if (failure instanceof Exception e) throw e;
            throw new RuntimeException(failure);
        }
    }
    public boolean refreshSession() throws Throwable {
        checkUsable();

        MemorySegment newSession =
                callObjectGetterAllowNull(manager, 6, "GetCurrentSession");

        if (session != null && session.address() != 0) {
            releaseComObject(session);
        }

        session = newSession;

        return session != null && session.address() != 0;
    }

}
