/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloV4Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener  {

    //region "Properties"
    private int FIXED_SIZE_STACK = 5; // tamanho fixo para a pilha
    private float SPEED_SOUND = 1.0f; // (1.0 = normal, range 0.5 to 2.0)
    private float DISTANCE_LIMIT = 200f; // tamanho limite para considerar (em cm, já que foi usado a altura média em cm)

    private static final Logger LOGGER = new Logger();

    //region "Layout"
    private Stack<ResultDetection> stackClasses;
    private Hashtable<String, Float> labelHeight = new Hashtable<>();
    private Hashtable<String, Integer> labelPriority = new Hashtable<>();
    private SeekBar seekBar;
    //endregion

    //region "Detection"
    private int TF_OD_API_INPUT_SIZE = getSize();
    private String TF_OD_API_MODEL_FILE = getModelFile();
    private final String TF_OD_API_LABELS_FILE = getLabelFile();
    private final boolean TF_OD_API_IS_QUANTIZED = getIsQuantized();

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;
    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;

    public int getSize() {
        return MainActivity.SIZE;
    }
    public String getModelFile() { return MainActivity.MODEL_FILE; }
    public String getLabelFile() { return MainActivity.LABEL_FILE; }
    public boolean getIsQuantized() { return MainActivity.IS_QUANTIZED; }
    public Float getAverageHeight(String className) {
        return labelHeight.get(className);
    }
    public Integer getPriority(String className) {
        return labelPriority.get(className);
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API
    }
    //endregion "Detection"

    //endregion

    MediaPlayer mediaPlayer;
    MediaPlayer mediaPlayer2;
    MediaPlayer mediaPlayer3;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(savedInstanceState);

        //region "AverageHeight and Priority File"
        AssetManager assetManager = getAssets();
        InputStream labelsInput = null;
        try {
            labelsInput = assetManager.open(TF_OD_API_LABELS_FILE);
            BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
            String line;
            while ((line = br.readLine()) != null) {
                String[] item = line.split(",");
                labelHeight.put(item[0], Float.parseFloat(item[1]));
                labelPriority.put(item[0], Integer.parseInt(item[2]));
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //endregion "AverageHeight and Priority File"

        //region "SeekBar"
        TextView textViewProgress = (TextView)findViewById(R.id.textViewProgress);
        textViewProgress.setText(String.valueOf(1.0f));
        seekBar = findViewById(R.id.seekBar);
        seekBar.incrementProgressBy(1);
        seekBar.setMax(10);
        seekBar.setProgress(2);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                // (1.0 = normal, range 0.5 to 2.0)
                float value = getConvertedValue(i);
                textViewProgress.setText(String.valueOf(value));
                SPEED_SOUND = value;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        //endregion "SeekBar"

        // region "MediaPlayer"
        mediaPlayer = new MediaPlayer();
        mediaPlayer2 = new MediaPlayer();
        mediaPlayer3 = new MediaPlayer();
        //endregion "MediaPlayer"

        //region "StackClasses"
        stackClasses = new Stack<ResultDetection>() {
            public ResultDetection push(ResultDetection item) {
                if (this.size() == FIXED_SIZE_STACK) {
                    this.removeElementAt(0);
                }

                if(!mediaPlayer.isPlaying() && !mediaPlayer2.isPlaying() && !mediaPlayer3.isPlaying()) {
                    mediaPlayer.reset();
                    Integer id = getResources().getIdentifier(item.getSoundName(), "raw", getPackageName());
                    mediaPlayer = MediaPlayer.create(getApplicationContext(), id);

                    mediaPlayer2.reset();
                    id = getResources().getIdentifier(item.getSoundDistance(), "raw", getPackageName());
                    mediaPlayer2 = MediaPlayer.create(getApplicationContext(), id);

                    mediaPlayer3.reset();
                    id = getResources().getIdentifier(item.getPositionFile(), "raw", getPackageName());
                    mediaPlayer3 = MediaPlayer.create(getApplicationContext(), id);

                    mediaPlayer.setOnPreparedListener(mp -> {
                        mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(SPEED_SOUND));
                        mediaPlayer.setNextMediaPlayer(mediaPlayer2);
                    });

                    mediaPlayer2.setOnPreparedListener(mp -> {
                        mediaPlayer2.setNextMediaPlayer(mediaPlayer3);
                    });

                    mediaPlayer.setOnCompletionListener(mp -> {
                        mediaPlayer2.setPlaybackParams(mediaPlayer2.getPlaybackParams().setSpeed(SPEED_SOUND));
                    });

                    mediaPlayer2.setOnCompletionListener(mp -> {
                        mediaPlayer3.setPlaybackParams(mediaPlayer3.getPlaybackParams().setSpeed(SPEED_SOUND));
                    });
                }

                return super.push(item);
            }
        };
        //endregion "StackClasses"
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mediaPlayer.release();
        mediaPlayer2.release();
        mediaPlayer3.release();
        super.onDestroy();
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    YoloV4Classifier.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        Log.e("CHECK", "run: " + results.size());

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        //region "Priority"
                        if(results.size() > 0) {
                            Collections.sort(results, new Comparator<Classifier.Recognition>() {
                                @Override
                                public int compare(Classifier.Recognition o1, Classifier.Recognition o2) {
                                    if (getPriority(o1.getTitle()) > getPriority(o2.getTitle())) {
                                        return 1;
                                    } else if (getPriority(o1.getTitle()) < getPriority(o2.getTitle())) {
                                        return -1;
                                    }
                                    return 0;
                                }
                            });
                        }
                        //endregion "Priority"

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);

                                //region "PositionOnView"

                                float x = result.getX();
                                float auxWidth = croppedBitmap.getWidth() / 3;
                                EPositionOnView positionOnView = EPositionOnView.CENTER;
                                if(x < auxWidth)
                                    positionOnView = EPositionOnView.LEFT;
                                if(x > (auxWidth * 2))
                                    positionOnView = EPositionOnView.RIGHT;

                                //endregion "PositionOnView"

                                //region "Distance"

                                // distância aproximada, porém necessita da altura ou largura média de cada classe
                                float objectImageHeight = result.getLocation().height();
                                float averageRealHeight = getAverageHeight(result.getTitle()); //altura média em cm

                                float distance = CameraActivity.LENS_INFO_AVAILABLE_FOCAL_LENGTHS[0] * averageRealHeight / objectImageHeight;
                                LOGGER.d(String.format("Distance of %s is %f", result.getTitle(), distance));

                                //endregion "Distance"

                                // Pilha para salvar os últimos FIZED_SIZE_STACK objetos detectados
                                if(distance < DISTANCE_LIMIT) // Filtrar os objetos até x de distância
                                    stackClasses.push(new ResultDetection(result.getTitle(), positionOnView, distance));
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        String finalClassesText = stackClasses.size() > 0 ? stackClasses.peek().getClassName() + ": " + stackClasses.peek().getDistance() : "";
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                        showLastClasses(finalClassesText);
                                    }
                                });
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    private float getConvertedValue(int intVal){
        float floatVal = 0.0f;
        floatVal = .5f * intVal;
        return floatVal;
    }
}
