package com.protocolbook.model;
public class Acquisition {
    private String kv, ma, rotationTime, pitch, detector, sliceThickness, interval, fieldOfView, matrix;
    private String minMa, maxMa, noiseIndex;
    public String getKv(){return kv;} public void setKv(String v){kv=v;}
    public String getMa(){return ma;} public void setMa(String v){ma=v;}
    public String getRotationTime(){return rotationTime;} public void setRotationTime(String v){rotationTime=v;}
    public String getPitch(){return pitch;} public void setPitch(String v){pitch=v;}
    public String getDetector(){return detector;} public void setDetector(String v){detector=v;}
    public String getSliceThickness(){return sliceThickness;} public void setSliceThickness(String v){sliceThickness=v;}
    public String getInterval(){return interval;} public void setInterval(String v){interval=v;}
    public String getFieldOfView(){return fieldOfView;} public void setFieldOfView(String v){fieldOfView=v;}
    public String getMatrix(){return matrix;} public void setMatrix(String v){matrix=v;}
    public String getMinMa(){return minMa;} public void setMinMa(String v){minMa=v;}
    public String getMaxMa(){return maxMa;} public void setMaxMa(String v){maxMa=v;}
    public String getNoiseIndex(){return noiseIndex;} public void setNoiseIndex(String v){noiseIndex=v;}
}
