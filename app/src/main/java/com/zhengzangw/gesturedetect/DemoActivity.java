package com.zhengzangw.gesturedetect;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

public class DemoActivity extends AppCompatActivity {

    private Button btnConnect;
    private TextView left;
    private TextView right;
    private TextView center;

    private Client client;
    private Handler handler;

    private static final int GESTURE_DURATION_MS = 1280000; // 1.28 sec
    private static final int GESTURE_SAMPLES = 128;

    // region tensorflow
    private static final String MODEL_FILENAME = "file:///android_asset/frozen_optimized_quant.pb";

    private static final int NUM_CHANNELS = 3;
    private static final float DATA_NORMALIZATION_COEF = 9f;
    private static final int SMOOTHING_VALUE = 20;

    private static final String INPUT_NODE = "x_input";
    private static final String OUTPUT_NODE = "labels_output";
    private static final String[] OUTPUT_NODES = new String[]{OUTPUT_NODE};
    private static final long[] INPUT_SIZE = {1, GESTURE_SAMPLES, NUM_CHANNELS};
    private static final String[] labels = new String[]{"Right", "Left"};

    private final float[] outputScores = new float[labels.length];
    private final float[] recordingData = new float[GESTURE_SAMPLES * NUM_CHANNELS];
    private final float[] recognData = new float[GESTURE_SAMPLES * NUM_CHANNELS];
    private final float[] filteredData = new float[GESTURE_SAMPLES * NUM_CHANNELS];
    private int dataPos = 0;

    private TensorFlowInferenceInterface inferenceInterface;

    private HandlerThread handlerThread = new HandlerThread("worker");
    private Handler workHandler;
    // endregion

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private boolean recStarted = false;
    private long firstTimestamp = -1;

    @Override
    protected void onCreate(Bundle SavedInstanceStates){
        super.onCreate(SavedInstanceStates);
        setContentView(R.layout.activity_demo);

        btnConnect = (Button) findViewById(R.id.Connect);
        btnConnect.setOnClickListener(clickListener);
        left = (TextView) findViewById(R.id.Left);
        right = (TextView) findViewById(R.id.Right);
        center = (TextView) findViewById(R.id.Center);

        handler = new DisplayHandler();

        handlerThread.start();
        workHandler = new Handler(handlerThread.getLooper());

        // Load the TensorFlow model
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v){
            switch (v.getId()){
                case R.id.Connect:
                    client = new Client(8989);
                    client.startReader();
                    sensorManager.registerListener(sensorEventListener, accelerometer, GESTURE_DURATION_MS / GESTURE_SAMPLES,
                            workHandler);
                    break;
            }
        }
    };

    class DisplayHandler extends Handler{
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            switch (msg.arg1) {
                case 1: left.setText((String)msg.obj);
                case 2: center.setText((String)msg.obj);
            }
        }
    }

    class Client{
        private BufferedReader reader;
        private BufferedWriter writer;
        private ExecutorService mThreadPool;
        private Socket socket;
        private int port;
        private final String TAG = DemoActivity.class.getSimpleName();
        private Lock writeLock = new ReentrantLock();
        private Lock readLock = new ReentrantLock();
        private int prepared = 0;

        Client(int port) {
            this.port = port;
            mThreadPool = Executors.newCachedThreadPool();
            mThreadPool.execute(new ConnectThread());
        }

        boolean ready() { return prepared==1; }

        void startReader(){
            mThreadPool.execute(new ReaderThread());
        }

        void startWriter(String toSend){
            mThreadPool.execute(new WriterThread(toSend));
        }

        class ConnectThread implements Runnable {
            @Override
            public void run(){
                try {
                    socket = new Socket("192.168.1.102", port);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    prepared = 1;

                    Message message = Message.obtain();
                    message.arg1 = 1;
                    message.obj = "Connected";
                    handler.sendMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        class ReaderThread implements Runnable {
            @Override
            public void run(){
                readLock.lock();
                while (prepared==0);
                try {
                    String lineString;
                    while (!(lineString = reader.readLine()).equals("bye")) {
                        Message message = Message.obtain();
                        message.arg1 = 2;
                        message.obj = lineString;
                        handler.sendMessage(message);
                    }
                    Message message = Message.obtain();
                    message.arg1 = 1;
                    message.obj = "Disconnect";
                    handler.sendMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                readLock.unlock();
            }
        }

        class WriterThread implements Runnable {
            String toSend;
            WriterThread(String toSend){
                super();
                this.toSend = toSend;
            }

            @Override
            public void run(){
                writeLock.lock();
                while (prepared==0);
                try {
                    writer.write( toSend + "\n");
                    writer.flush();
                } catch (IOException e){
                    e.printStackTrace();
                }
                writeLock.unlock();
            }
        }
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (firstTimestamp == -1) firstTimestamp = event.timestamp;
            final float floatTimestampMicros = (event.timestamp - firstTimestamp) / 1000000f;

            recordingData[dataPos++] = event.values[0] / DATA_NORMALIZATION_COEF;
            recordingData[dataPos++] = event.values[1] / DATA_NORMALIZATION_COEF;
            recordingData[dataPos++] = event.values[2] / DATA_NORMALIZATION_COEF;
            if (dataPos >= recordingData.length) {
                dataPos = 0;
            }

            // recognize data
            // copy recordingData to recognData arranged
            System.arraycopy(recordingData, 0, recognData, recognData.length - dataPos, dataPos);
            System.arraycopy(recordingData, dataPos, recognData, 0, recordingData.length - dataPos);

            filterData(recognData, filteredData);

            long startTime = SystemClock.elapsedRealtimeNanos();
            inferenceInterface.feed(INPUT_NODE, filteredData, INPUT_SIZE);
            inferenceInterface.run(OUTPUT_NODES);
            inferenceInterface.fetch(OUTPUT_NODE, outputScores);
            long stopTime = SystemClock.elapsedRealtimeNanos();

            final float leftProbability = outputScores[0];
            final float rightProbability = outputScores[1];

            if (client.ready()) client.startWriter(""+leftProbability+" "+rightProbability);
        }
    };

    private static void filterData(float[] input, float[] output) {
        Arrays.fill(output, 0);

        float ir = 1.0f / SMOOTHING_VALUE;

        for (int i = 0; i < input.length; i += NUM_CHANNELS) {
            for (int j = 0; j < SMOOTHING_VALUE; j++) {
                if (i - j * NUM_CHANNELS < 0) continue;
                output[i + 0] += input[i + 0 - j * NUM_CHANNELS] * ir;
                output[i + 1] += input[i + 1 - j * NUM_CHANNELS] * ir;
                output[i + 2] += input[i + 2 - j * NUM_CHANNELS] * ir;
            }
        }
    }
}