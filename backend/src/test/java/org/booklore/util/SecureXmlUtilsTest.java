package org.booklore.util;

import org.grimmory.pdfium4j.XmpMetadataParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecureXmlUtilsTest {

    @Test
    void normalizesMissingRdfNamespaceBeforeParsing() throws Exception {
        String xml = """
                <rdf:RDF xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <rdf:Description>
                    <dc:title>Recovered title</dc:title>
                  </rdf:Description>
                </rdf:RDF>
                """;

        String normalized = SecureXmlUtils.normalizeMissingRdfNamespace(xml);
        var document = SecureXmlUtils.parseXml(normalized, true);

        assertThat(normalized)
                .contains("xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
        assertThat(document.getElementsByTagNameNS("*", "title").item(0).getTextContent())
                .isEqualTo("Recovered title");
    }

    @Test
    void leavesDeclaredRdfNamespaceUnchanged() {
        String xml = """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/>
                """;

        assertThat(SecureXmlUtils.normalizeMissingRdfNamespace(xml)).isEqualTo(xml);
    }

    @Test
    void normalizedXmlDoesNotEmitFatalErrorsFromPdfiumXmpParser() {
        String xml = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF>
                    <rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>Recovered title</dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        ByteArrayOutputStream parserErrors = new ByteArrayOutputStream();

        var metadata = capturePdfiumParserErrors(xml, parserErrors);

        assertThat(metadata.title()).contains("Recovered title");
        assertThat(parserErrors.toString(StandardCharsets.UTF_8)).doesNotContain("[Fatal Error]");
    }

    private org.grimmory.pdfium4j.model.XmpMetadata capturePdfiumParserErrors(
            String xml,
            ByteArrayOutputStream parserErrors) {
        synchronized (SecureXmlUtilsTest.class) {
            PrintStream originalError = System.err;
            try (PrintStream capturedError = new PrintStream(parserErrors, true, StandardCharsets.UTF_8)) {
                System.setErr(capturedError);
                String normalized = SecureXmlUtils.normalizeMissingRdfNamespace(xml);
                return XmpMetadataParser.parse(normalized.getBytes(StandardCharsets.UTF_8));
            } finally {
                System.setErr(originalError);
            }
        }
    }
}
