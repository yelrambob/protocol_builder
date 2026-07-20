package com.protocolbook.parser;

import com.protocolbook.model.Protocol;

import java.io.File;
import java.util.List;

public interface ProtocolParser {

    List<Protocol> parse(File workbook) throws Exception;

}
