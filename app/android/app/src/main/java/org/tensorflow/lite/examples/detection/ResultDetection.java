package org.tensorflow.lite.examples.detection;

public class ResultDetection {

    public ResultDetection(String className, EPositionOnView positionOnView, float distance) {
        this.className = className;
        this.positionOnView = positionOnView;
        this.distance = distance;
    }

    public String getClassName() {
        return className;
    }

    public String getSoundName() {
        return className.replace(' ', '_');
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public EPositionOnView getPositionOnView() {
        return positionOnView;
    }

    public String getPositionFile() {
        return "position_" + getPositionOnView().getValue();
    }

    public void setPositionOnView(EPositionOnView positionOnView) {
        this.positionOnView = positionOnView;
    }

    public float getDistance() {
        return distance;
    }

    public String getSoundDistance() {
        String fileName = "ten_centimeters";
        if(distance > 0.1)
            fileName = "ten_centimeters";
        if(distance > 0.2)
            fileName = "twenty_centimeters";
        if(distance > 0.3)
            fileName = "thirty_centimeters";
        if(distance > 0.4)
            fileName = "forty_centimeters";
        if(distance > 0.5)
            fileName = "fifty_centimeters";
        if(distance > 0.6)
            fileName = "sixty_centimeters";
        if(distance > 0.7)
            fileName = "seventy_centimeters";
        if(distance > 0.8)
            fileName = "eighty_centimeters";
        if(distance > 0.9)
            fileName = "ninety_centimeters";
        if(distance > 1)
            fileName = "one_meter";
        if(distance > 2)
            fileName = "two_meter";
        if(distance > 3)
            fileName = "three_meter";
        if(distance > 4)
            fileName = "four_meter";
        if(distance > 5)
            fileName = "five_meter";
        if(distance > 6)
            fileName = "six_meter";
        if(distance > 7)
            fileName = "seven_meter";
        if(distance > 8)
            fileName = "eight_meter";
        if(distance > 9)
            fileName = "nine_meter";
        if(distance > 10)
            fileName = "ten_meter";
        if(distance > 20)
            fileName = "twenty_meter";
        if(distance > 30)
            fileName = "thirty_meter";
        if(distance > 40)
            fileName = "forty_meter";
        if(distance > 50)
            fileName = "fifty_meter";
        if(distance > 60)
            fileName = "sixty_meter";
        if(distance > 70)
            fileName = "seventy_meter";
        if(distance > 80)
            fileName = "eighty_meter";
        if(distance > 90)
            fileName = "ninety_meter";
        if(distance > 100)
            fileName = "hundred_meter";
        if(distance > 200)
            fileName = "two_hundred_meter";

        return fileName;
    }


    public void setDistance(float distance) {
        this.distance = distance;
    }

    private String className;
    private EPositionOnView positionOnView;
    private float distance;

}
