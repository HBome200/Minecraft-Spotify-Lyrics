package dev.along.spotifylyrics.backend;

public abstract class MusicManager { //I changed it to abstract class, u cannot create interface objects
    public abstract String getArtist();
    public abstract String getTitle();
    public abstract String getAlbum();
    public abstract long getDuration();
    public abstract long getPosition();
    public abstract boolean isPlaying();
}
