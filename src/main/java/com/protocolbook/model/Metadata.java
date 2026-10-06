package com.protocolbook.model;
public class Metadata {
    private String name, category, bodyPart, version, scanner, clinicalIndication;
    private String protocolNumber, patientType, library, uuid, lastUpdated;
    private String displayNumber;
    private Integer section;
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
    public String getLastUpdated(){return lastUpdated;} public void setLastUpdated(String v){lastUpdated=v;}
    /**
     * The number shown in the book, when it isn't protocolNumber - "" for scanners that don't number their
     * protocols (Siemens), where protocolNumber is only the key protocol-overrides.json is matched on.
     */
    public String getDisplayNumber(){return displayNumber;} public void setDisplayNumber(String v){displayNumber=v;}
    /**
     * The book section (the same codes as a GE protocol number's whole-number prefix: 1 Head ... 9 Lower Ext.),
     * for protocols whose number doesn't carry one. Null means "take it from the protocol number".
     */
    public Integer getSection(){return section;} public void setSection(Integer v){section=v;}
}
