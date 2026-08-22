package dev.along.spotifylyrics.backend;

import java.io.IOException;
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

        String[] lines = syncedLyrics.split("\\R");

        for (String line : lines) {
            line = line.trim();

            if (!line.startsWith("[") || !line.contains("]")) {
                continue;
            }

            try {
                int closingBracket = line.indexOf("]");

                String timestampStr =
                        line.substring(1, closingBracket);

                String text =
                        line.substring(closingBracket + 1).trim();

                String[] timeParts = timestampStr.split(":");

                if (timeParts.length != 2) {
                    continue;
                }

                long minutes = Long.parseLong(timeParts[0]);

                String[] secondParts =
                        timeParts[1].split("\\.", 2);

                long seconds =
                        Long.parseLong(secondParts[0]);

                long milliseconds = 0;

                if (secondParts.length > 1) {
                    String fraction = secondParts[1];

                    if (fraction.length() == 1) {
                        milliseconds = Long.parseLong(fraction) * 100;
                    } else if (fraction.length() == 2) {
                        milliseconds = Long.parseLong(fraction) * 10;
                    } else {
                        milliseconds = Long.parseLong(
                                fraction.substring(0, 3)
                        );
                    }
                }

                long timestamp =
                        (minutes * 60 + seconds) * 1000
                                + milliseconds;

                lyrics.add(new LyricsLine(timestamp, text));

            } catch (NumberFormatException ignored) {
                // Invalid timestamp. Ignore this line.
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
                .GET()
                .build();

        try {


            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if(response.statusCode() != 200) {
                System.err.println("lrclib returned " + response.statusCode());
                return null;
            }
            return response;
        } catch (IOException e) {
            System.err.println("Failed to fetch lyrics" + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

    }
    public String getSyncedLyrics(HttpResponse<String> response) {
        if(response == null) {
            return null;
        }
        if(response.statusCode() != 200) {
            return null;
        }
        try {


            JsonObject json =
                    JsonParser.parseString(response.body()).getAsJsonObject();


            if (!json.has("syncedLyrics") || json.get("syncedLyrics").isJsonNull()) {
                return null;
            }

            return json.get("syncedLyrics").getAsString();
        } catch (RuntimeException e) {
            System.err.println("Failed to parse lrclib response" + e.getMessage());
            return null;
        }
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