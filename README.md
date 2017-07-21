AMixer
=======

Welcome to AVMixer, a mediacodec based library for Android.

AVMixer is:
- An media mixing library based on MediaCodec.
- An Android library, developed for API 18 (Android 4.3). While some of the code
  may work with older versions of Android, no effort will be made to
  support them.
- Open source (Apache 2 license), copyright by Google.  So you can use the
  code according to the terms of the mentioned license.
- A constantly work-in-progress.  It's updated whenever it needs to.


Current features
----------------

Version 1.8

- Fixed occasional erroneous mixed audio presentation time
- Fixed issue with the audio tracks volume on the mixing process. The set overall and individual audio track set volume should now work properly.

Version 1.7

- Fixed a fast forward video issue happening occasionally when a voice track was added.

Version 1.6

- Fixed the presentation time for audio mixing when adding mp3s file and high res. video with an audio track. 


Version 1.5

- Fixed ocassional crash with the audio mixing when having 3 or more audio files.


Version 1.4

- Added equalizer feature for each audio track individually
- Fixed 1080p resolution size
- Changed algorithm to parse the mixing when having just one audio track to process to guarantee a higher rate of the final mixing success


Version 1.3

- Added get/set public methods to the audio/video sync flag on the AudioVideo mixing class
- Improved error handling for the internal mixing process (Audio/Video only and Audio and Video mixing)
- Performance improvements


Version 1.2

- Added support to convert portrait to landscape mode all images added to the mixing process
- Adjusted audio mixing sample rate
- Improved mixing performance and memory usage
- Fixed audio mixing when different audio delays were set for each audio track


Version 1.1

- Added support for multiple videos in the mixing process
- Added Support for 3GPP/3GP2 video format
- Added framerate support for 15, 30 and 60 FPS
- Added an offset to mute a portion of the mixed video's audio.
- Now audio and video can be synced in the mixing process,
  ending both at the same time or remain unsynced
- Now the original videos's sound may or may not be considered in the mixing process
- Now for cloning tracks, a source media filenamepath and an output filenamepath can be specified
- Fixed audio's track start delay


Version 1.0

- Video and audios mixing using the MPEG4 container on .M4A and .MP4 for audio and video formats, respectively.
- Video and images mixing using .MP4 format as output.
- Video, images and audio mixing using M4A and .MP4 for audio and video formats, respectively.
- Video track extraction and cropping using .MP4 format as output. 
- Audio tracks mixing overlapping them in a single audio track using .M4A as output format
- Audio track extraction and cropping from an existing video file with audio. The extraction uses the .M4A format as output
- Adds an audio start delay on the video and audio mixing process.
- Normalize (increase or decrease) the audio track volume on the mixing process
- Crops audio tracks start on video and audio mixing process.
- Converts any audio format to .M4A format
- Generates a .zip file from a mixing object and vice-versa
- Generates videos in the following resolutions: 360p, 480p, 720p and 1080p


Known issues
------------

- The mixing object parsing does not save the information regarding audio tracks delay time, audio tracks initial cropping, audio volume set,
  and images presentation time set.

Features, Improvements & fix ideas
-----------------------------------

- Add start and end time for video and audio cropping. Currently the cropping is done by specifing an start and end frame/chunk.
- Add support for 4k resolution for the mixing output video
- Join audio and video tracks in a single audio or video track, where they can be played after each other.