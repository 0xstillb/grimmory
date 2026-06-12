package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class SecureXmlUtils {

    // DocumentBuilderFactory is thread-safe after configuration cache one per namespace-aware mode
    private static final DocumentBuilderFactory NS_AWARE_FACTORY;
    private static final DocumentBuilderFactory NON_NS_AWARE_FACTORY;
    private static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final Pattern RDF_ROOT_PATTERN = Pattern.compile("<rdf:RDF\\b([^>]*)>");
    private static final Pattern RDF_NAMESPACE_PATTERN = Pattern.compile("\\bxmlns:rdf\\s*=");

    static {
        try {
            NS_AWARE_FACTORY = buildFactory(true);
            NON_NS_AWARE_FACTORY = buildFactory(false);
        } catch (ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DocumentBuilderFactory buildFactory(boolean namespaceAware) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);

        // Prevent XXE attacks
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        return factory;
    }

    private static DocumentBuilderFactory getFactory(boolean namespaceAware) {
        return namespaceAware ? NS_AWARE_FACTORY : NON_NS_AWARE_FACTORY;
    }

    public static DocumentBuilder createSecureDocumentBuilder(boolean namespaceAware) 
            throws ParserConfigurationException {
        // newDocumentBuilder() is NOT thread-safe must create new builder each time
        return getFactory(namespaceAware).newDocumentBuilder();
    }

    public static Document parseXml(String xml, boolean namespaceAware) throws Exception {
        try {
            return parseStrict(xml, namespaceAware);
        } catch (SAXParseException e) {
            if (!namespaceAware || !isMissingRdfNamespace(e, xml)) {
                throw e;
            }
            return parseStrict(normalizeMissingRdfNamespace(xml), true);
        }
    }

    public static String normalizeMissingRdfNamespace(String xml) {
        if (xml == null) {
            return xml;
        }
        Matcher rootMatcher = RDF_ROOT_PATTERN.matcher(xml);
        if (!rootMatcher.find() || RDF_NAMESPACE_PATTERN.matcher(rootMatcher.group(1)).find()) {
            return xml;
        }
        String normalizedRoot = "<rdf:RDF xmlns:rdf=\"" + RDF_NAMESPACE + "\"" + rootMatcher.group(1) + ">";
        return rootMatcher.replaceFirst(Matcher.quoteReplacement(normalizedRoot));
    }

    private static Document parseStrict(String xml, boolean namespaceAware) throws Exception {
        var builder = createSecureDocumentBuilder(namespaceAware);
        builder.setErrorHandler(new SilentErrorHandler());
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static boolean isMissingRdfNamespace(SAXParseException error, String xml) {
        return error.getMessage() != null
                && error.getMessage().contains("The prefix \"rdf\" for element \"rdf:RDF\" is not bound")
                && !normalizeMissingRdfNamespace(xml).equals(xml);
    }

    private static final class SilentErrorHandler extends DefaultHandler {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }
}
