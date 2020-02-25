package ie.tcd.netlab.objecttracker.detectors;

import android.media.Image;

import java.util.List;

import ie.tcd.netlab.objecttracker.helpers.Recognition;

public abstract class Detector {

    public abstract Detections recognizeImage(Image image, int rotation); // take Image as input
    public Detections recognize(byte[] yuv, int image_w, int image_h, int rotation) {
        // just a stub
        return new Detections();
    }
    public List<Recognition> onDetections(Image image, int rotation, List<Recognition> results){
        return results;
    }
}
