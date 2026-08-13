package dev.along.spotifylyrics.backend;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt64;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;

public class LinuxMusicManager extends MusicManager {

    private DBusConnection connection;
    private final Properties properties;

    private final List<LyricsLine> lyrics;
    private final LyricsManager lyricsManager;

    public LinuxMusicManager() throws Exception {
        try {
            connection = DBusConnectionBuilder
                    .forSessionBus()
                    .build();
            System.out.println("Connected to D-Bus!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        properties = connection.getRemoteObject("org.mpris.MediaPlayer2.spotify", "/org/mpris/MediaPlayer2", Properties.class);
        System.out.println("Spotify status: " + getPlaybackStatus());
        LinkedHashMap<String, Object> metadata = getMetadata();

        String artist = ((List<String>) metadata.get("xesam:artist")).get(0);
        String title = (String) metadata.get("xesam:title");
        String album = (String) metadata.get("xesam:album");
        long duration = ((UInt64) metadata.get("mpris:length")).longValue() / 1_000_000;

        System.out.println("Artist: " + artist);
        System.out.println("Title: " + title);
        System.out.println("Album: " + album);
        System.out.println("Duration: " + duration);


        this.lyricsManager = new LyricsManager();

        HttpResponse<String> response =
                lyricsManager.fetchLyrics(artist, title, album, duration);

        String syncedLyrics =
                lyricsManager.getSyncedLyrics(response);
        System.out.println("SYNCED LYRICS IS NULL: " + (syncedLyrics == null));
        System.out.println("SYNCED LYRICS: " + syncedLyrics);

        this.lyrics =
                lyricsManager.parseLyrics(syncedLyrics);

        System.out.println("HTTP status: " + response.statusCode());
        System.out.println("HTTP status: " + response.statusCode());
        System.out.println("Lyrics lines: " + lyrics.size());



    }
    public String getPlaybackStatus() {
        return properties.Get("org.mpris.MediaPlayer2.Player", "PlaybackStatus");
    }
    public LinkedHashMap<String, Object> getMetadata() {
        return properties.Get("org.mpris.MediaPlayer2.Player", "Metadata");
    }
    public long getPosition() {
        long position = properties.Get("org.mpris.MediaPlayer2.Player", "Position");
        return position / 1000;
    }

    @Override
    public boolean isPlaying() {
        String status = properties.Get("org.mpris.MediaPlayer2.Player", "PlaybackStatus");
        return status.equals("Playing");
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
    public String getArtist() {
        return ((List<String>) getMetadata().get("xesam:artist")).getFirst();
    }
    public String getTitle() {
        return (String) getMetadata().get("xesam:title");
    }
    public String getAlbum() {
        return (String) getMetadata().get("xesam:album");
    }
    public long getDuration() {
        return ((UInt64) getMetadata().get("mpris:length")).longValue() / 1_000_000;
    }
}