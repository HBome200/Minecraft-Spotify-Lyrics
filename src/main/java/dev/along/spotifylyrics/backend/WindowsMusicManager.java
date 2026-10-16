package dev.along.spotifylyrics.backend;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.util.List;


public class WindowsMusicManager extends MusicManager {
    private static String artist;
    private static String album;
    private static String title;
    private static long duration;
    private static long position;
    private static String playbackStatus = "STOPPED";

    private static LyricsManager lyricsManager;
    private static List<LyricsLine> lyrics;
    private static boolean initialFetchDone = false;

    public WindowsMusicManager() throws Exception {
        Thread bridgeThread = new Thread(WindowsMusicManager::startBridge);
        bridgeThread.setDaemon(true);
        bridgeThread.start();
    }

    public static void startBridge() {
        try {
            ProcessBuilder builder = new ProcessBuilder("C:\\along-bridge\\windows-bridge.exe");
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                parseSpotifyData(line);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseSpotifyData(String data) {
        try {
            if (data == null || !data.contains("|")) {
                System.out.println("Bridge Output: " + data);
                return;
            }

            String[] lines = data.split("\\|");
            if (lines.length < 6) {
                return;
            }
            title = lines[0].replaceAll("TITLE:", "");
            artist = lines[1].replaceAll("ARTIST:", "");
            duration = Long.parseLong(lines[3].replaceAll("DURATION:", "")) / 1_000_000;
            position = Long.parseLong(lines[2].replaceAll("POSITION:", "")) / 1000;
            album = lines[4].replaceAll("ALBUM:", "");
            playbackStatus = lines[5].replaceAll("PLAYBACK STATUS:", "");

            if (!initialFetchDone && title != null && !title.isEmpty()) {
                initialFetchDone = true;
                System.out.println("Artist: " + artist);
                System.out.println("Title: " + title);
                System.out.println("Album: " + album);
                System.out.println("Duration: " + duration);
                System.out.println("Position: " + position);
                System.out.println("Playback Status: " + playbackStatus);

                lyricsManager = new LyricsManager();

                HttpResponse<String> response =
                        lyricsManager.fetchLyrics(artist, title, album, duration);

                String syncedLyrics =
                        lyricsManager.getSyncedLyrics(response);
                System.out.println("SYNCED LYRICS IS NULL: " + (syncedLyrics == null));
                System.out.println("SYNCED LYRICS: " + syncedLyrics);

                lyrics = lyricsManager.parseLyrics(syncedLyrics);

                System.out.println("HTTP status: " + response.statusCode());
                System.out.println("Lyrics lines: " + (lyrics != null ? lyrics.size() : 0));
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        return duration;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public boolean isPlaying() {
        return playbackStatus.equals("PLAYING");
    }
    public List<LyricsLine> getLyrics() {
        return lyrics;
    }
    public LyricsLine getCurrentLine(
            List<LyricsLine> lyrics,
            long position
    ) {
        LyricsLine current = null;

        for (LyricsLine line : lyrics) {
            if (line.getTimeStamp() <= position) {
                current = line;
            } else {
                break;
            }
        }

        return current;
    }
    public LyricsLine getCurrentLine() {
        long position = getPosition() / 1000;
        return lyricsManager.getCurrentLine(
                lyrics,
                position
        );
    }
}
