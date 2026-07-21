package com.protocolbook.model;
public class Contrast {
    private boolean iv, oral, rectal; private String timing, notes;
    public boolean isIv(){return iv;} public void setIv(boolean v){iv=v;}
    public boolean isOral(){return oral;} public void setOral(boolean v){oral=v;}
    public boolean isRectal(){return rectal;} public void setRectal(boolean v){rectal=v;}
    public String getTiming(){return timing;} public void setTiming(String v){timing=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
}
