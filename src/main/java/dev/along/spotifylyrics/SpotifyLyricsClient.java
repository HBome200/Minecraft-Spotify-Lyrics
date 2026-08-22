package dev.along.spotifylyrics;

import dev.along.spotifylyrics.backend.LinuxMusicManager;
import dev.along.spotifylyrics.backend.LyricsLine;
import dev.along.spotifylyrics.backend.LyricsManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.freedesktop.dbus.exceptions.DBusException;

import java.net.http.HttpResponse;
import java.util.List;

public class SpotifyLyricsClient implements ClientModInitializer {

    private static String previousLyric = "";
    private static String currentLyric = "Spotify Lyrics";
    private static String nextLyric = "";

    private static final int MARGIN = 15;
    private static final int LINE_SPACING = 18;
    private static LyricsLine currentLine;
    private static LinuxMusicManager spotifyManager;
    private static LyricsManager lyricsManager;
    private static List<LyricsLine> lyrics;
    private static String currentTrack = "";

    @Override
    public void onInitializeClient() {
        try {
            spotifyManager = new LinuxMusicManager();;
            lyricsManager = new LyricsManager();
        } catch (DBusException e) {
            System.err.println("Spotify lyrics, could not connect via DBus to Spotify");
            spotifyManager = null;
            lyricsManager = new LyricsManager();
        } catch (Exception e) {
            System.err.println("Spotify Lyrics: failed to initialize");
            spotifyManager = null;
            lyricsManager = new LyricsManager();
        }
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(
                        SpotifyLyrics.MOD_ID,
                        "lyrics"
                ),
                SpotifyLyricsClient::renderLyrics
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (spotifyManager == null) {
                return;
            }

            String artist = spotifyManager.getArtist();
            String title = spotifyManager.getTitle();

            if (artist == null || artist.isBlank()
                    || title == null || title.isBlank()) {
                return;
            }

            String track = artist + " - " + title;

            if (lyrics == null || lyrics.isEmpty()) {
                return;
            }
            if (!track.equals(currentTrack)) {
                currentTrack = track;

                System.out.println("SONG CHANGED: " + currentTrack);

                currentLine = null;
                clearLyrics();

                try {
                    HttpResponse<String> response = lyricsManager.fetchLyrics(
                            spotifyManager.getArtist(),
                            spotifyManager.getTitle(),
                            spotifyManager.getAlbum(),
                            spotifyManager.getDuration()
                    );

                    String syncedLyrics =
                            lyricsManager.getSyncedLyrics(response);

                    lyrics = lyricsManager.parseLyrics(syncedLyrics);

                    System.out.println("Lyrics lines: " + lyrics.size());

                } catch (Exception e) {
                    e.printStackTrace();
                    lyrics = null;
                }

                return;
            }




            long position = spotifyManager.getPosition();

            int currentIndex =
                    lyricsManager.getCurrentLineIndex(lyrics, position);

            if (currentIndex == -1) {
                return;
            }

            LyricsLine current = lyrics.get(currentIndex);

            if (currentLine == null ||
                    current.getTimeStamp() != currentLine.getTimeStamp()) {

                currentLine = current;

                String previous =
                        currentIndex > 0
                                ? lyrics.get(currentIndex - 1).getText()
                                : "";

                String next =
                        currentIndex < lyrics.size() - 1
                                ? lyrics.get(currentIndex + 1).getText()
                                : "";

                setLyrics(
                        previous,
                        current.getText(),
                        next
                );

                System.out.println(
                        "LYRIC [" + position + "ms]: "
                                + current.getText()
                );
            }
        });

    }

    private static void renderLyrics(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        int x = MARGIN;
        int y = MARGIN;

        // Previous lyric
        if (!previousLyric.isEmpty()) {
            graphics.text(
                    minecraft.font,
                    previousLyric,
                    x,
                    y,
                    0x99FFFFFF,
                    true
            );
        }

        // Current lyric
        graphics.text(
                minecraft.font,
                currentLyric,
                x,
                y + LINE_SPACING,
                0xFFFFFFFF,
                true
        );

        // Next lyric
        if (!nextLyric.isEmpty()) {
            graphics.text(
                    minecraft.font,
                    nextLyric,
                    x,
                    y + LINE_SPACING * 2,
                    0x99FFFFFF,
                    true
            );
        }
    }

    // ===== Backend API =====

    public static void setLyrics(
            String previous,
            String current,
            String next
    ) {
        previousLyric = previous;
        currentLyric = current;
        nextLyric = next;
    }

    public static void setCurrentLyric(String lyric) {
        currentLyric = lyric;
    }

    public static void clearLyrics() {
        previousLyric = "";
        currentLyric = "";
        nextLyric = "";
    }
}