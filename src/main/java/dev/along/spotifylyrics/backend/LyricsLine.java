package dev.along.spotifylyrics.backend;

public class LyricsLine {
    private long timeStamp;
    private String text;

    public LyricsLine(long timeStamp, String text) {
        this.timeStamp = timeStamp;
        this.text = text;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return timeStamp + "      :       " + text;
    }
}
