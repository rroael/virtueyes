package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f; // Percentual a ser considerado para apresentar objeto como detectado
    public static int SIZE = 256;
    public static String MODEL_FILE;
    public static String LABEL_FILE;
    public static boolean IS_QUANTIZED = false;
    private Button btnStreet, btnHome, btnPark, btnBeach, btnStreet16, btnHome16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //region "Camera Real-Time"
        // Classes: person, bicycle, car, motorbike, bus, truck, traffic light
        btnStreet = findViewById(R.id.btnStreet);
        btnStreet.setOnClickListener(
                Init("yolov4-micro-street.tflite",
                        "coco-street.txt",
                        false)
        );

        btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(
                Init("yolov4-micro-home.tflite",
                        "coco-home.txt",
                        false)
        );

        btnPark = findViewById(R.id.btnPark);
        btnPark.setOnClickListener(
                Init("yolov4-micro-park.tflite",
                        "coco-park.txt",
                        false)
        );

        btnBeach = findViewById(R.id.btnBeach);
        btnBeach.setOnClickListener(
                Init("yolov4-micro-beach.tflite",
                        "coco-beach.txt",
                        false)
        );
        //endregion

    }

    private View.OnClickListener Init(String modelFile, String labelFile, boolean isQuantized){
        return new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                MODEL_FILE = modelFile;
                LABEL_FILE = labelFile;
                IS_QUANTIZED = isQuantized;
                startActivity(new Intent(MainActivity.this, DetectorActivity.class));
            }
        };
    }
}