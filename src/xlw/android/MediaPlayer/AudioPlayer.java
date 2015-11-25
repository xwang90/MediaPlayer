package xlw.android.MediaPlayer;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.util.Log;


public class AudioPlayer {
	private static final String TAG = "AudioPlayer";
	private static final long TIMEOUT = 10000;
	
	private MediaCodec mAudioDecoder;
	private MediaFormat mMediaFormat;
	
	private BufferInfo mBufferInfo;
	private ByteBuffer[] mInputBuffers;
	private ByteBuffer[] mOutputBuffers;
	
	private MediaExtractor extractor;
	private AudioTrack mAudioTrack;
	
	private String mSource;
	private boolean isDecoding = false;
	
	public void play(String source) {
		this.mSource = source;
		setFormat();
		initDecoder();
		startDecoder();
		playback();
	}

	public void stop() {
		isDecoding = false;
	}
	
	private void playback() {
		isDecoding = true;
		
		new Thread(new Runnable() {
			long startMs;
			
			@Override
			public void run() {
				mBufferInfo = new BufferInfo();
				startMs = System.currentTimeMillis();
				boolean isEOS = false;
				while (isDecoding) {
					int inIndex = mAudioDecoder.dequeueInputBuffer(TIMEOUT);
					if (inIndex >= 0) {
						ByteBuffer buffer = mInputBuffers[inIndex];
						if (!isEOS) {
							int size = extractor.readSampleData(buffer, 0);
							if (size < 0) {
								Log.d(TAG, "Audio InputBuffer BUFFER_FLAG_END_OF_STREAM");
								mAudioDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
								isEOS = true;
							} else {
								Log.d(TAG, "Audio Sample Size: " + size);
								mAudioDecoder.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
								
								if (!isEOS)
									nextSample();
							}
						}
					}

					processDequeueBuffer();
					
					
					if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						Log.d(TAG, "Audio OutputBuffer BUFFER_FLAG_END_OF_STREAM");
						break;
					}
				}
				
				mAudioDecoder.stop();
				mAudioDecoder.release();
				extractor.release();
				mAudioTrack.release();
			}

			private void nextSample() {
				
				while (extractor.getSampleTime() / 1000 > System.currentTimeMillis() - startMs) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				extractor.advance();
			}

			private void processDequeueBuffer() {
				int outIndex = mAudioDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT);
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d(TAG, "Audio INFO_OUTPUT_BUFFERS_CHANGED");
					mOutputBuffers = mAudioDecoder.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d(TAG, "Audio new format " + mAudioDecoder.getOutputFormat());
					mAudioTrack.setPlaybackRate(mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					Log.d(TAG, "Audio dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer tmpBuffer = mOutputBuffers[outIndex];
					final byte[] chunk = new byte[mBufferInfo.size];
					tmpBuffer.get(chunk); 
					tmpBuffer.clear();
					
					if (chunk.length > 0) {
						mAudioTrack.write(chunk, 0, chunk.length);
					}
					mAudioDecoder.releaseOutputBuffer(outIndex, false /* render */);
					break;
				}
			}
		}).start();
	}

	private void startDecoder() {
		mAudioDecoder.start();
		mInputBuffers = mAudioDecoder.getInputBuffers();
		mOutputBuffers = mAudioDecoder.getOutputBuffers();
	}

	private void initDecoder() {
		String mime = mMediaFormat.getString(MediaFormat.KEY_MIME);
		try {
			mAudioDecoder = MediaCodec.createDecoderByType(mime);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mAudioDecoder.configure(mMediaFormat, null, null, 0);
		mAudioTrack.play();
	}

	private void setFormat() {
		extractor = new MediaExtractor();
		try {
			extractor.setDataSource(mSource);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (int i = 0; i < extractor.getTrackCount(); i++) {
			mMediaFormat = extractor.getTrackFormat(i);
			String mime = mMediaFormat.getString(MediaFormat.KEY_MIME);
			Log.d(TAG, "setFormat - i: " + i + " mMediaFormat: " + mMediaFormat + " mime: " + mime);
			if (mime.startsWith("audio/")) {
				extractor.selectTrack(i);
				int audioBufSize = AudioTrack.getMinBufferSize(mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
	                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
				mAudioTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC,
					mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
					mMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
					AudioFormat.ENCODING_PCM_16BIT,
					audioBufSize,
					AudioTrack.MODE_STREAM
				);
				break;
			}
		}
	}
}
