
package dev.along.spotifylyrics.backend;

public final class WindowsMusicManager extends MusicManager
        implements AutoCloseable {

    private volatile String artist = "";
    private volatile String album = "";
    private volatile String title = "";
    private volatile long duration;
    private volatile long position;
    private volatile boolean playing;

    private volatile boolean running = true;
    private final Thread worker;

    private volatile long positionCapturedNanos = System.nanoTime();

    public WindowsMusicManager() {
        worker = new Thread(this::runWorker, "Spotify-Lyrics-Windows-Media");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWorker() {
        // IMPORTANT: open, use, and close the session on this same thread.
        while (running) {
            try (WindowsMediaSession media = WindowsMediaSession.open()) {
                while (running) {
                    try {
                        if (!media.refreshSession()) {
                            Thread.sleep(1000);
                            continue;
                        }

                        String newArtist = media.getArtist();
                        String newTitle = media.getTitle();
                        String newAlbum = media.getAlbum();
                        boolean newPlaying = media.isPlaying();
                        long newDuration = media.getDurationMs();
                        long newPosition = media.getPositionMs(newPlaying);

                        artist = newArtist;
                        title = newTitle;
                        album = newAlbum;
                        duration = newDuration;
                        position = newPosition;
                        positionCapturedNanos = System.nanoTime();
                        playing = newPlaying;
                    } catch (Throwable e) {
                        // Keep the last valid snapshot if a read fails.
                        System.err.println(
                                "[Spotify Lyrics] Windows media read failed: "
                                        + e.getMessage()
                        );
                    }

                    Thread.sleep(250);
                }
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
            } catch (Throwable e) {
                if (running) {
                    System.err.println(
                            "[Spotify Lyrics] Windows media initialization failed: "
                                    + e.getMessage()
                    );
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException interrupted) {
                        if (!running) {
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getArtist() {
        return artist;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getAlbum() {
        return album;
    }

    @Override
    public long getDuration() {
        return duration / 1000;
    }

    @Override
    public long getPosition() {
        long current = position;

        if (playing) {
            long elapsedMs =
                    (System.nanoTime() - positionCapturedNanos) / 1_000_000L;
            current += Math.max(0L, elapsedMs);
        }

        long currentDuration = duration;
        if (currentDuration > 0) {
            current = Math.min(current, currentDuration);
        }

        return Math.max(0L, current);
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
    }
}
