package com.protocolbook;

import com.protocolbook.parser.GEWorkbookParser;

import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {

        File workbook = new File("Protocols.xlsm");

        GEWorkbookParser parser = new GEWorkbookParser();

        var protocols = parser.parse(workbook);

        System.out.println("Protocols Found: " + protocols.size());

    }
}
