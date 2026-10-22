package com.nexora.staff;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.File;

/** Lightweight .xlsx reader so NEXORA does not need a large spreadsheet runtime dependency. */
public final class XlsxSheetReader {
    private XlsxSheetReader() {}

    public static List<List<String>> read(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            Map<Integer, String> shared = readSharedStrings(zip);
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheet == null) throw new IllegalArgumentException("The workbook does not contain a first worksheet.");
            DocumentBuilderFactory factory = secureFactory();
            Document doc = factory.newDocumentBuilder().parse(zip.getInputStream(sheet));
            NodeList rows = doc.getElementsByTagName("row");
            List<List<String>> result = new ArrayList<>();
            for (int i = 0; i < rows.getLength(); i++) {
                Element row = (Element) rows.item(i);
                NodeList cells = row.getElementsByTagName("c");
                int maxCol = -1;
                Map<Integer, String> values = new HashMap<>();
                for (int c = 0; c < cells.getLength(); c++) {
                    Element cell = (Element) cells.item(c);
                    String ref = cell.getAttribute("r");
                    int col = columnIndex(ref);
                    maxCol = Math.max(maxCol, col);
                    String value = cellValue(cell, shared);
                    values.put(col, value);
                }
                List<String> line = new ArrayList<>();
                for (int c = 0; c <= maxCol; c++) line.add(values.getOrDefault(c, ""));
                result.add(line);
            }
            return result;
        }
    }

    private static Map<Integer, String> readSharedStrings(ZipFile zip) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) return map;
        DocumentBuilderFactory factory = secureFactory();
        Document doc = factory.newDocumentBuilder().parse(zip.getInputStream(entry));
        NodeList strings = doc.getElementsByTagName("si");
        for (int i = 0; i < strings.getLength(); i++) {
            Element si = (Element) strings.item(i);
            NodeList texts = si.getElementsByTagName("t");
            StringBuilder value = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) value.append(texts.item(j).getTextContent());
            map.put(i, value.toString());
        }
        return map;
    }

    private static String cellValue(Element cell, Map<Integer, String> shared) {
        String type = cell.getAttribute("t");
        NodeList values = cell.getElementsByTagName("v");
        if ("inlineStr".equals(type)) {
            NodeList texts = cell.getElementsByTagName("t");
            return texts.getLength() == 0 ? "" : texts.item(0).getTextContent();
        }
        if (values.getLength() == 0) return "";
        String raw = values.item(0).getTextContent();
        if ("s".equals(type)) {
            try { return shared.getOrDefault(Integer.parseInt(raw), ""); } catch (NumberFormatException ignored) { return raw; }
        }
        return raw;
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static int columnIndex(String cellRef) {
        int i = 0, value = 0;
        while (i < cellRef.length() && Character.isLetter(cellRef.charAt(i))) {
            value = value * 26 + (Character.toUpperCase(cellRef.charAt(i)) - 'A' + 1);
            i++;
        }
        return value - 1;
    }
}
