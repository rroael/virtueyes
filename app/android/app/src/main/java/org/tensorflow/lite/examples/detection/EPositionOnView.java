package org.tensorflow.lite.examples.detection;

public enum EPositionOnView {
    LEFT("left"),
    RIGHT("right"),
    CENTER("center");

    private String value;

    EPositionOnView(String value) {
        this.value = value;
    }

    public String getValue(){
        return value;
    }
}
