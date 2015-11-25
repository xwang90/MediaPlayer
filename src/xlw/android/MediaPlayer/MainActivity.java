package xlw.android.MediaPlayer;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
	private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/VID_20151125_122454.mp4";
	private static final String TAG = "MainActivity";
	private VideoPlayerThread mVideoPlayer = null;
	private AudioPlayer mAudioPlayer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurfaceView sv = new SurfaceView(this);
		sv.getHolder().addCallback(this);
		setContentView(sv);
	}

	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (mVideoPlayer == null) {
			mVideoPlayer = new VideoPlayerThread(holder.getSurface());
			mVideoPlayer.start();
		}
		
		if (mAudioPlayer == null) {
			mAudioPlayer = new AudioPlayer();
			mAudioPlayer.play(SAMPLE);
		}
		
		
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mVideoPlayer != null) {
			mVideoPlayer.interrupt();
		}
		
		if (mAudioPlayer != null) {
			mAudioPlayer.stop();
		}
	}

	private class VideoPlayerThread extends Thread {
		private MediaExtractor extractor;
		private MediaCodec mVideoDecoder;
		private Surface surface;

		public VideoPlayerThread(Surface surface) {
			this.surface = surface;
		}

		@Override
		public void run() {
			extractor = new MediaExtractor();
			try {
				extractor.setDataSource(SAMPLE);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			for (int i = 0; i < extractor.getTrackCount(); i++) {
				MediaFormat format = extractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.startsWith("video/")) {
					extractor.selectTrack(i);
					try {
						mVideoDecoder = MediaCodec.createDecoderByType(mime);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					mVideoDecoder.configure(format, surface, null, 0);
					break;
				}
			}

			if (mVideoDecoder == null) {
				Log.e(TAG, "Video Decoder fail to be created!");
				return;
			}

			mVideoDecoder.start();

			ByteBuffer[] inputBuffers = mVideoDecoder.getInputBuffers();
			ByteBuffer[] outputBuffers = mVideoDecoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			boolean isEOS = false;
			long startMs = System.currentTimeMillis();

			while (!Thread.interrupted()) {
				if (!isEOS) {
					int inIndex = mVideoDecoder.dequeueInputBuffer(10000);
					if (inIndex >= 0) {
						ByteBuffer buffer = inputBuffers[inIndex];
						int sampleSize = extractor.readSampleData(buffer, 0);
						if (sampleSize < 0) {
							
							Log.d(TAG, "Video InputBuffer BUFFER_FLAG_END_OF_STREAM");
							mVideoDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							isEOS = true;
						} else {
							mVideoDecoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
							extractor.advance();
						}
					}
				}

				int outIndex = mVideoDecoder.dequeueOutputBuffer(info, 10000);
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d(TAG, "Video INFO_OUTPUT_BUFFERS_CHANGED");
					outputBuffers = mVideoDecoder.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d(TAG, "Video new format " + mVideoDecoder.getOutputFormat());
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					Log.d(TAG, "Video dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer buffer = outputBuffers[outIndex];
					Log.v(TAG, "Video --- output buffer: " + buffer);

					
					while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
						try {
							sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
							break;
						}
					}
					mVideoDecoder.releaseOutputBuffer(outIndex, true);
					break;
				}

				
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}
			}

			mVideoDecoder.stop();
			mVideoDecoder.release();
			extractor.release();
		}
	}
}
