package com.protocolbook.model;
public class Reconstruction {
    private String name, kernel, thickness, interval, plane, matrix, dfov;
    private String windowLevel, windowWidth, iterativeConfig, startLocation, endLocation;
    private Integer numberOfImages;
    private boolean derived;
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getKernel(){return kernel;} public void setKernel(String v){kernel=v;}
    public String getThickness(){return thickness;} public void setThickness(String v){thickness=v;}
    public String getInterval(){return interval;} public void setInterval(String v){interval=v;}
    public String getPlane(){return plane;} public void setPlane(String v){plane=v;}
    public String getMatrix(){return matrix;} public void setMatrix(String v){matrix=v;}
    public String getDfov(){return dfov;} public void setDfov(String v){dfov=v;}
    public String getWindowLevel(){return windowLevel;} public void setWindowLevel(String v){windowLevel=v;}
    public String getWindowWidth(){return windowWidth;} public void setWindowWidth(String v){windowWidth=v;}
    public String getIterativeConfig(){return iterativeConfig;} public void setIterativeConfig(String v){iterativeConfig=v;}
    public String getStartLocation(){return startLocation;} public void setStartLocation(String v){startLocation=v;}
    public String getEndLocation(){return endLocation;} public void setEndLocation(String v){endLocation=v;}
    public Integer getNumberOfImages(){return numberOfImages;} public void setNumberOfImages(Integer v){numberOfImages=v;}
    public boolean isDerived(){return derived;} public void setDerived(boolean v){derived=v;}
}
