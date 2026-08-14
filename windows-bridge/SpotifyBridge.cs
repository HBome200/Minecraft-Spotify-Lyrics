using Windows.Media.Control;

namespace SpotifyLyricsBridge;

public static class SpotifyBridge
{
    public static async Task TestAsync()
    {
        var manager =
            await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();

        var session = manager.GetCurrentSession();

        if (session == null)
        {
            Console.WriteLine("No current media session.");
            return;
        }

        var props = await session.TryGetMediaPropertiesAsync();

        Console.WriteLine($"App:    {session.SourceAppUserModelId}");
        Console.WriteLine($"Artist: {props.Artist}");
        Console.WriteLine($"Title:  {props.Title}");
        Console.WriteLine($"Album:  {props.AlbumTitle}");
    }
}