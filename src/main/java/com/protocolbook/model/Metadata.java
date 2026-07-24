package com.protocolbook.model;
public class Metadata {
    private String name, category, bodyPart, version, scanner, clinicalIndication;
    private String protocolNumber, patientType, library, uuid;
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getBodyPart(){return bodyPart;} public void setBodyPart(String v){bodyPart=v;}
    public String getVersion(){return version;} public void setVersion(String v){version=v;}
    public String getScanner(){return scanner;} public void setScanner(String v){scanner=v;}
    public String getClinicalIndication(){return clinicalIndication;} public void setClinicalIndication(String v){clinicalIndication=v;}
    public String getProtocolNumber(){return protocolNumber;} public void setProtocolNumber(String v){protocolNumber=v;}
    public String getPatientType(){return patientType;} public void setPatientType(String v){patientType=v;}
    public String getLibrary(){return library;} public void setLibrary(String v){library=v;}
    public String getUuid(){return uuid;} public void setUuid(String v){uuid=v;}
}
