# Nacho Music
Nacho Music is a music player and downloader that uses a fork of YT-DLP for Android.
## Functions
### Download Music
You can head into the `Download Page` to download music, there you can add a video or music URL (Check YT-DLP compatibility), song title, song album, song author and tags, there is autocompletion based on downloaded songs and you can also toggle autocompletion based on song, so that when you download it you don't need to set the value manually.

When downloading playlists you have to set atleat the song title to autocomplete field with the button next to the input, if you don't all songs will have the same name.

You will get progress and success notifications when downloading.

### Play Music
> For now songs play in a linear order and using the search feature wont change the timeline order, you also can't rearange songs for now.

#### Music Page
You can head into the `Music Page` to play any of your downloaded songs, you will have a searchbar with autocomplete on top, a song list and your current playing song.
* Click a song to start playing it
* Click the current song preview to quickly scroll to your currently playing song
* Hold a song to edit it, a edit menu will open.

#### Player Page
This is a more detailed version of the `Music Page` here you can see the song name, album artwork, seek inside the song with the progress bar, skip to next or return to last, pause or play.

You also have a button to change playmode, there are different ones:
* Normal: When a song ends you will continue to the next one until there are no more songs left.
* Shuffle: After finishing a song you will go to another song picked by chance.
* Repeat: The song will keep looping after it ends.
* Stop after end: After this song stops playback will be paused.

### Settings Page
Here you have a few buttons, you can delete ALL songs permanenty, load songs that are inside the `emulated\storage\0\Android\data\com.saltichash.musicapp\files\music\` folder but not inside `music.json` and also check the data files (sometimes doesn't work)