package com.protocolbook.model;
public class Contrast {
    private boolean iv, oral, rectal; private String timing, notes;
    private String ivVolume, ivConcentration, oralVolume, oralConcentration;
    private String flowRate, injectionDelay, injectionDuration, mediaRatio;
    public boolean isIv(){return iv;} public void setIv(boolean v){iv=v;}
    public boolean isOral(){return oral;} public void setOral(boolean v){oral=v;}
    public boolean isRectal(){return rectal;} public void setRectal(boolean v){rectal=v;}
    public String getTiming(){return timing;} public void setTiming(String v){timing=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public String getIvVolume(){return ivVolume;} public void setIvVolume(String v){ivVolume=v;}
    public String getIvConcentration(){return ivConcentration;} public void setIvConcentration(String v){ivConcentration=v;}
    public String getOralVolume(){return oralVolume;} public void setOralVolume(String v){oralVolume=v;}
    public String getOralConcentration(){return oralConcentration;} public void setOralConcentration(String v){oralConcentration=v;}
    public String getFlowRate(){return flowRate;} public void setFlowRate(String v){flowRate=v;}
    public String getInjectionDelay(){return injectionDelay;} public void setInjectionDelay(String v){injectionDelay=v;}
    public String getInjectionDuration(){return injectionDuration;} public void setInjectionDuration(String v){injectionDuration=v;}
    public String getMediaRatio(){return mediaRatio;} public void setMediaRatio(String v){mediaRatio=v;}
}
