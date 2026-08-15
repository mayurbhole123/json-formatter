package com.jsontools.service;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * XML parse / beautify / minify / validate. Parsers are configured with
 * external entity resolution switched off (XXE protection) since the input
 * is arbitrary text pasted by a user.
 */
@Service
public class XmlService {

    public Document parse(String xml) throws Exception {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("No input provided. Paste or upload some XML first.");
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        secure(f);
        DocumentBuilder builder = f.newDocumentBuilder();
        builder.setErrorHandler(null);
        Document doc = builder.parse(new InputSource(new StringReader(xml.trim())));
        doc.getDocumentElement().normalize();
        return doc;
    }

    private void secure(DocumentBuilderFactory f) {
        trySet(f, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySet(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySet(f, "http://xml.org/sax/features/external-general-entities", false);
        trySet(f, "http://xml.org/sax/features/external-parameter-entities", false);
        trySet(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setXIncludeAware(false);
    }

    private void trySet(DocumentBuilderFactory f, String feature, boolean value) {
        try {
            f.setFeature(feature, value);
        } catch (Exception ignored) {
            // Not every parser implementation knows every feature.
        }
    }

    public String format(String xml, String indent) throws Exception {
        Document doc = parse(xml);
        stripWhitespaceNodes(doc.getDocumentElement());
        return serialize(doc, JsonService.indentToken(indent).length(), true);
    }

    public String minify(String xml) throws Exception {
        Document doc = parse(xml);
        stripWhitespaceNodes(doc.getDocumentElement());
        return serialize(doc, 0, false);
    }

    /**
     * Removes text nodes that are pure whitespace, otherwise the transformer's
     * indent option layers new indentation on top of the old and the result drifts.
     */
    private void stripWhitespaceNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getTextContent() == null || child.getTextContent().trim().isEmpty()) {
                    node.removeChild(child);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripWhitespaceNodes(child);
            }
        }
    }

    private String serialize(Document doc, int indentWidth, boolean indent) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setAttribute("indent-number", indentWidth);
        } catch (IllegalArgumentException ignored) {
            // Some factories reject the attribute; the OutputKeys below still apply.
        }
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indentWidth));

        StringWriter out = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(out));
        String result = out.toString();
        // The transformer puts the declaration and root on one line; split them.
        result = result.replaceFirst("\\?>(?!\\n)", "?>" + (indent ? "\n" : ""));
        return result.trim();
    }

    public static class Validation {
        private final boolean valid;
        private final String message;
        private final int line;
        private final int column;

        Validation(boolean valid, String message, int line, int column) {
            this.valid = valid;
            this.message = message;
            this.line = line;
            this.column = column;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }

    public Validation validate(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return new Validation(false, "No input provided.", 0, 0);
        }
        try {
            parse(xml);
            return new Validation(true, "Valid XML", 0, 0);
        } catch (SAXParseException e) {
            return new Validation(false, e.getMessage(), e.getLineNumber(), e.getColumnNumber());
        } catch (Exception e) {
            return new Validation(false, e.getMessage(), 0, 0);
        }
    }
}
