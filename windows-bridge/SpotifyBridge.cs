using System.Runtime.InteropServices;

namespace SpotifyLyricsBridge;

public static class SpotifyBridge
{
    [UnmanagedCallersOnly(EntryPoint = "spotify_test")]
    public static int Test()
    {
        return 69420;
    }
}