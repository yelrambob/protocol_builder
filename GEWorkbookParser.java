package com.protocolbook.parser;

import com.protocolbook.model.Protocol;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GEWorkbookParser implements ProtocolParser {

    @Override
    public List<Protocol> parse(File workbook) throws Exception {

        System.out.println("Opening workbook...");

        List<Protocol> protocols = new ArrayList<>();

        // TODO:
        // Open workbook with Apache POI
        // Detect protocol sheets
        // Build Protocol objects
        // Return list

        return protocols;

    }
}
