using System;
using System.Threading.Tasks;
using Windows.Media.Control;

namespace SpotifyBridge
{
    public static class Program
    {
        public static async Task Main(string[] args)
        {
            var manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();

            while (true)
            {
                var session = manager.GetCurrentSession();
                if (session != null)
                {
                    var props = await session.TryGetMediaPropertiesAsync();
                    var timeline = session.GetTimelineProperties();
                    var playbackInfo = session.GetPlaybackInfo();

                    // Outputs the pipe-delimited data for your Java mod to parse
                    Console.WriteLine($"TITLE:{props.Title}|ARTIST:{props.Artist}|POSITION:{timeline.Position.Ticks}|DURATION:{timeline.EndTime.Ticks}|ALBUM:{props.AlbumTitle}|PLAYBACK STATUS:{playbackInfo.PlaybackStatus}");
                }

                await Task.Delay(1000);
            }
        }
    }
}