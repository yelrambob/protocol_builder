# protocol_builder
inorder to parse protocols in an easier to read format. 


GEProtocolBook
│
├── build.gradle
├── settings.gradle
├── README.md
│
└── src
    └── main
        ├── java
        │   └── com
        │       └── protocolbook
        │           ├── Main.java
        │           │
        │           ├── model
        │           │      Protocol.java
        │           │      Metadata.java
        │           │      PatientSetup.java
        │           │      Contrast.java
        │           │      Acquisition.java
        │           │      Dose.java
        │           │      Series.java
        │           │      Reconstruction.java
        │           │      Timing.java
        │           │
        │           ├── parser
        │           │      ProtocolParser.java
        │           │      GEWorkbookParser.java
        │           │
        │           ├── generator
        │           │      JsonExporter.java
        │           │
        │           └── util
        │
        └── resources
            ├── css
            └── templates
