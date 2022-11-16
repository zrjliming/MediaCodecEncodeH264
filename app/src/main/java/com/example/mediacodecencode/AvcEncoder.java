package com.example.mediacodecencode;


import java.io.IOException;
import java.nio.ByteBuffer;
import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;


public class AvcEncoder
{
	private final static String TAG = "AvcEncoder";
	
	private int TIMEOUT_USEC = 12000;

	private MediaCodec mediaCodec;
	int m_width;
	int m_height;
	int m_framerate;
	String m_name;
	byte[] m_info = null;
	 
	public byte[] configbyte; 


	@SuppressLint("NewApi")
	public AvcEncoder(int width, int height, int framerate, int bitrate,String name) {
		
		m_width  = width;
		m_height = height;
		m_framerate = framerate;
		m_name = name;
//		4608000 1500000
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
		//		// 码率模式
//		// CQ 不控制码率, 尽最大可能保证图像质量
//		// CBR 静态码率
//		// VBR 动态码率, 根据图像内容的复杂度来动态调整输出码率, 图像复杂则码率高, 图像简单则码率低
		mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000); // 码率
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30); // 帧率
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 20); // 帧间间隔, 单位秒
//		mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline); // H264 BP
//		mediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);


	    try {
			mediaCodec = MediaCodec.createEncoderByType("video/avc");
//			mediaCodec = MediaCodec.createByCodecName("OMX.hisi.video.encoder.avc");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
	    mediaCodec.start();
	}

	@SuppressLint("NewApi")
	private void StopEncoder() {
	    try {
	        mediaCodec.stop();
	        mediaCodec.release();
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	}

	public boolean isRuning = false;
	
	public void StopThread(){
		isRuning = false;
        try {
        	StopEncoder();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	int count = 0;

	public void StartEncoderThread(){
		Thread EncoderThread = new Thread(new Runnable() {

			@SuppressLint("NewApi")
			@Override
			public void run() {
				isRuning = true;
				byte[] input = null;
				long pts =  0;
				long generateIndex = 0;
				long count = 0;

				while (isRuning) {
					if (MainActivity.YUVQueue.size() >0){
						input = MainActivity.YUVQueue.poll();
						byte[] yuv420sp = new byte[m_width*m_height*3/2];
						NV21ToNV12(input,yuv420sp,m_width,m_height);
						input = yuv420sp;
					}
					if (input != null) {
						try {

							ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
							ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
							int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
							if (inputBufferIndex >= 0) {
								pts = computePresentationTime(generateIndex);
								ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
								inputBuffer.clear();
								inputBuffer.put(input);
								mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
								generateIndex += 1;
							}
							
							MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
							int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
							while (outputBufferIndex >= 0) {

								ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
								// 从缓冲区获取编码成H264的byte[]
								byte[] outData = new byte[outputBuffer.remaining()];
								outputBuffer.get(outData);
								count++;
								Log.i(TAG, " name "+m_name+"Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+" count "+count);
								// 编码得到H264文件，保存起来调试使用
								Util.writeFileToSDCard(outData, "H264", "test"+m_name+"1.h264", true, false);
								check(outData);
								mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
							}

						} catch (Throwable t) {
							t.printStackTrace();
						}
					} else {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});
		EncoderThread.start();
		
	}
	
	private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
		if(nv21 == null || nv12 == null) {
			return;
		}
		int framesize = width*height;
		int i = 0,j = 0;
		System.arraycopy(nv21, 0, nv12, 0, framesize);
		for(i = 0; i < framesize; i++){
			nv12[i] = nv21[i];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
		  nv12[framesize + j-1] = nv21[j+framesize];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
		  nv12[framesize + j] = nv21[j+framesize-1];
		}
	}
	
    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

	private void check(byte[] data) {
		int index = 4; // 00 00 00 01
		if (data[2] == 0X1) { // 00 00 01
			index = 3;
		}
		// NALU的数据类型,header 1个字节的后五位
		int naluType = (data[index]&(0x1F));
		if (naluType == 7) {
			Log.e(TAG, "SPS");
		} else if (naluType == 8) {
			Log.e(TAG, "PPS");
		} else if (naluType == 5) {
			Log.e(TAG, "IDR");
		} else {
//			Log.d(TAG, "非IDR=" + naluType +" data index "+data[index]);
		}
	}

}
