package dev.along.spotifylyrics.backend;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class LyricsManager {
    private final HttpClient client = HttpClient.newHttpClient();
    public List<LyricsLine> parseLyrics(String syncedLyrics) {



        List<LyricsLine> lyrics = new ArrayList<>();

        if (syncedLyrics == null || syncedLyrics.isBlank()) {
            return lyrics;
        }
        String[] lines = syncedLyrics.split("\n");

        for (String line : lines) {
            if (line.startsWith("[") && line.contains("]")) {

                String timestampStr =
                        line.substring(1, line.indexOf("]"));

                String text =
                        line.substring(line.indexOf("]") + 1).trim();

                String[] timeParts = timestampStr.split(":");

                long minutes = Long.parseLong(timeParts[0]);

                String[] secondParts =
                        timeParts[1].split("\\.");

                long seconds =
                        Long.parseLong(secondParts[0]);

                long hundredths =
                        secondParts.length > 1
                                ? Long.parseLong(secondParts[1])
                                : 0;

                long timestamp =
                        (minutes * 60 + seconds) * 1000
                                + hundredths * 10;

                lyrics.add(new LyricsLine(timestamp, text));
            }
        }

        return lyrics;
    }

    private String buildUrl(
            String artist,
            String title,
            String album,
            long duration
    ) {
        return "https://lrclib.net/api/get?"
                + "artist_name="
                + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                + "&track_name="
                + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&album_name="
                + URLEncoder.encode(album, StandardCharsets.UTF_8)
                + "&duration="
                + duration;
    }

    public HttpResponse<String> fetchLyrics(
            String artist,
            String title,
            String album,
            long duration
    ) throws Exception {

        String url = buildUrl(
                artist,
                title,
                album,
                duration
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MinecraftSpotifyLyricsMod/1.0 (your_email@example.com)") // Added User-Agent header
                .GET()
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    public String getSyncedLyrics(HttpResponse<String> response) {
        JsonObject json =
                JsonParser.parseString(response.body()).getAsJsonObject();

        if (!json.has("syncedLyrics") || json.get("syncedLyrics").isJsonNull()) {
            return null;
        }

        return json.get("syncedLyrics").getAsString();
    }

    public LyricsLine getCurrentLine(List<LyricsLine> lyrics, long position) {
        LyricsLine currentLine = null;

        for (LyricsLine line : lyrics) {
            if (line.getTimeStamp() > position) {
                break;
            }
            if(!line.getText().isBlank())
                currentLine = line;
        }

        return currentLine;
    }
    public int getCurrentLineIndex(List<LyricsLine> lyrics, long position) {

        int currentIndex = -1;

        for (int i = 0; i < lyrics.size(); i++) {
            LyricsLine line = lyrics.get(i);

            if (line.getTimeStamp() > position) {
                break;
            }

            if (!line.getText().isBlank()) {
                currentIndex = i;
            }
        }

        return currentIndex;
    }
}