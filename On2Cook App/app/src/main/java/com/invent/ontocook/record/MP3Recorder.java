package com.invent.ontocook.record;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Debug;
import android.os.Environment;
import android.os.Message;
import android.util.Log;

import com.invent.ontocook.utils.DebugLog;
import com.naman14.androidlame.AndroidLame;
import com.naman14.androidlame.LameBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MP3Recorder {
    // =======================AudioRecord Default
    // Settings=======================
    /* private static final int DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
     *//**
     * 以下三项为默认配置参数。Google Android文档明确表明只有以下3个参数是可以在所有设备上保证支持的。
     *//*
    private static final int DEFAULT_SAMPLING_RATE = 44100;// 模拟器仅支持从麦克风输入8kHz采样率
    private static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    *//**
     * 下面是对此的封装 private static final int DEFAULT_AUDIO_FORMAT =
     * AudioFormat.ENCODING_PCM_16BIT;
     *//*

    // ======================Lame Default Settings=====================
    private static final int DEFAULT_LAME_MP3_QUALITY = 0;
    *//**
     * 与DEFAULT_CHANNEL_CONFIG相关，因为是mono单声，所以是1
     *//*
    private static final int DEFAULT_LAME_IN_CHANNEL = 1;
    *//**
     * Encoded bit rate. MP3 file will be encoded with bit rate 32kbps
     *//*
    private static final int DEFAULT_LAME_MP3_BIT_RATE = 32;

    // ==================================================================

    *//**
     * 自定义 每160帧作为一个周期，通知一下需要进行编码
     *//*
    private static final int FRAME_COUNT = 160;
    private AudioRecord mAudioRecord = null;
    private int mBufferSize;
    private short[] mPCMBuffer;
    private DataEncodeThread mEncodeThread;
    private boolean mIsRecording = false;
    private File mRecordFile;

    */
    /**
     * Default constructor. Setup recorder with default sampling rate 1 channel,
     * 16 bits pcm
     *//*
     */
    int minBuffer;
    int inSamplerate = 8000;

    String TAG = this.getClass().getCanonicalName();

    boolean isRecording = false;

    AudioRecord audioRecord;
    AndroidLame androidLame;
    FileOutputStream outputStream;

    /*public MP3Recorder(File recordFile) {
        mRecordFile = recordFile;
    }*/


    public void startRecording(File file) {
        isRecording = true;

        minBuffer = AudioRecord.getMinBufferSize(inSamplerate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        Log.e(TAG, "Initialising audio recorder..");
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC, inSamplerate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2);

        //5 seconds data
        Log.e(TAG, "creating short buffer array");
        short[] buffer = new short[inSamplerate * 2 * 5];

        // 'mp3buf' should be at least 7200 bytes long
        // to hold all possible emitted data.
        Log.e(TAG, "creating mp3 buffer");
        byte[] mp3buffer = new byte[(int) (7200 + buffer.length * 2 * 1.25)];
//        File file = new File(filePath);
//        if (!file.exists()) {
//            try {
//                file.createNewFile();
//            } catch (Exception e) {
//                Log.e(TAG, "startRecording: e" + e.getMessage());
//            }
//        }
        try {
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Log.e(TAG, "Initialising Andorid Lame");
        androidLame = new LameBuilder()
                .setInSampleRate(inSamplerate)
                .setOutChannels(1)
                .setOutBitrate(32)
                .setOutSampleRate(inSamplerate)
                .build();

        Log.e(TAG, "started audio recording");
        updateStatus("Recording...");
        audioRecord.startRecording();

        int bytesRead = 0;

        while (isRecording) {

            Log.e(TAG, "reading to short array buffer, buffer sze- " + minBuffer);
            bytesRead = audioRecord.read(buffer, 0, minBuffer);
            Log.e(TAG, "bytes read=" + bytesRead);

            if (bytesRead > 0) {

                Log.e(TAG, "encoding bytes to mp3 buffer..");
                int bytesEncoded = androidLame.encode(buffer, buffer, bytesRead, mp3buffer);
                Log.e(TAG, "bytes encoded=" + bytesEncoded);

                if (bytesEncoded > 0) {
                    try {
                        Log.e(TAG, "writing mp3 buffer to outputstream with " + bytesEncoded + " bytes");
                        outputStream.write(mp3buffer, 0, bytesEncoded);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        updateStatus("Recording stopped");

        Log.e(TAG, "flushing final mp3buffer");
        int outputMp3buf = androidLame.flush(mp3buffer);
        Log.e(TAG, "flushed " + outputMp3buf + " bytes");

        if (outputMp3buf > 0) {
            try {
                Log.e(TAG, "writing final mp3buffer to outputstream");
                outputStream.write(mp3buffer, 0, outputMp3buf);
                Log.e(TAG, "closing output stream");
                outputStream.close();
                updateStatus("Output recording saved in " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            Log.e(TAG, "releasing audio recorder");
            audioRecord.stop();
            audioRecord.release();
            Log.e(TAG, "closing android lame");
        } catch (Exception e) {
            e.printStackTrace();
        }
        androidLame.close();
        isRecording = false;

        /**
         * Start recording. Create an encoding thread. Start record from this
         * thread.
         *
         * @throws IOException
         */
    /*public void start() throws IOException {
        if (mIsRecording)
            return;
        initAudioRecorder();
        mAudioRecord.startRecording();
        new Thread() {

            @Override
            public void run() {
                // 设置线程权限
                android.os.Process
                        .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                mIsRecording = true;
                while (mIsRecording) {
                    int readSize = mAudioRecord
                            .read(mPCMBuffer, 0, mBufferSize);
                    if (readSize > 0) {
                        mEncodeThread.addTask(mPCMBuffer, readSize);
                        calculateRealVolume(mPCMBuffer, readSize);
                    }
                }



                // release and finalize audioRecord
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
                // stop the encoding thread and try to wait
                // until the thread finishes its job
                Message msg = Message.obtain(mEncodeThread.getHandler(),
                        DataEncodeThread.PROCESS_STOP);
                msg.sendToTarget();
            }

            *//**
         * 此计算方法来自samsung开发范例
         *
         * @param buffer
         *            buffer
         * @param readSize
         *            readSize
         *//*
            private void calculateRealVolume(short[] buffer, int readSize) {
                int sum = 0;
                for (int i = 0; i < readSize; i++) {
                    // 这里没有做运算的优化，为了更加清晰的展示代码
                    sum += buffer[i] * buffer[i];
                }
                if (readSize > 0) {
                    double amplitude = sum / readSize;
                    mVolume = (int) Math.sqrt(amplitude);
                }
            }

            ;
        }.start();
    }
*/
        /*private int mVolume;

        public int getVolume () {
            return mVolume;
        }

        private static final int MAX_VOLUME = 2000;

        public int getMaxVolume () {
            return MAX_VOLUME;
        }*/

   /* public void stop() {
        mIsRecording = false;
    }

    public boolean isRecording() {
        return mIsRecording;
    }


    *//**
         * Initialize audio recorder
         *//*
    private void initAudioRecorder() throws IOException {
        mBufferSize = AudioRecord.getMinBufferSize(DEFAULT_SAMPLING_RATE,
                DEFAULT_CHANNEL_CONFIG, AudioFormat.ENCODING_PCM_16BIT);



        *//* Setup audio recorder *//*
        mAudioRecord = new AudioRecord(DEFAULT_AUDIO_SOURCE,
                DEFAULT_SAMPLING_RATE, DEFAULT_CHANNEL_CONFIG,
                AudioFormat.ENCODING_PCM_16BIT, mBufferSize);

        // Check if AGC is supported, if so retrieve from shared prefs
        if (AudioEffectUtil.INSTANCE.isSupported(AudioEffect.EFFECT_TYPE_AGC)) {
            AutomaticGainControl gainControl = AutomaticGainControl.create(mAudioRecord.getAudioSessionId());
            if (gainControl != null)
                gainControl.setEnabled(true);
        }

        //  Check if Noise Suppression is supported, if so retrieve from shared prefs
        if (AudioEffectUtil.INSTANCE.isSupported(AudioEffect.EFFECT_TYPE_NS)) {
            NoiseSuppressor noiseSupp = NoiseSuppressor.create(mAudioRecord.getAudioSessionId());
            if (noiseSupp != null)
                noiseSupp.setEnabled(true);
        }


        int bytesPerFrame = 2016;//mAudioRecord.getBufferSizeInFrames();

        *//*
         * Get number of samples. Calculate the buffer size (round up to the
         * factor of given frame size) 使能被整除，方便下面的周期性通知
         *//*
        int frameSize = mBufferSize / bytesPerFrame;
        if (frameSize % FRAME_COUNT != 0) {
            frameSize += (FRAME_COUNT - frameSize % FRAME_COUNT);
            mBufferSize = frameSize * bytesPerFrame;
        }

        mPCMBuffer = new short[mBufferSize];
        *//*
         * Initialize lame buffer mp3 sampling rate is the same as the recorded
         * pcm sampling rate The bit rate is 32kbps
         *//*
        MP3Encoder.init(DEFAULT_SAMPLING_RATE, DEFAULT_LAME_IN_CHANNEL,
                DEFAULT_SAMPLING_RATE, DEFAULT_LAME_MP3_BIT_RATE,
                DEFAULT_LAME_MP3_QUALITY);
        // Create and run thread used to encode data
        // The thread will
        mEncodeThread = new DataEncodeThread(mRecordFile, mBufferSize);
        mEncodeThread.start();
        mAudioRecord.setRecordPositionUpdateListener(mEncodeThread,
                mEncodeThread.getHandler());
        mAudioRecord.setPositionNotificationPeriod(FRAME_COUNT);
    }*/
    }

    public void stop() {
//        audioRecord.stop();
//        audioRecord.release();
        Log.e(TAG, "closing android lame");
//        androidLame.close();
        isRecording = false;
//        Log.e(TAG, "Stop ");
    }

    private void updateStatus(final String status) {
        Log.e(TAG, "Status Record" + status);
    }
}