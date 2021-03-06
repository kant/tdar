package org.tdar.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.TestConstants;
import org.tdar.core.service.SerializationService;
import org.xml.sax.SAXException;

public class JaxbSchemaValidator {

    public transient Logger logger = LoggerFactory.getLogger(getClass());
    private SerializationService serializationService;
    private Validator v;

    public JaxbSchemaValidator(SerializationService serializationService) throws FileNotFoundException, SAXException {
        this.serializationService = serializationService;
        setupSchemaMap();
        setupValidator();
    }

    List<File> files = new ArrayList<>();

    public void setupSchemaMap() throws FileNotFoundException {
        String base = TestConstants.TEST_ROOT_DIR + "schemaCache";
        files.add(TestConstants.getFile(base, "mods3.3.xsd"));
        files.add(TestConstants.getFile(base, "oai-identifier.xsd"));
        files.add(TestConstants.getFile(base, "oaidc.xsd"));
        files.add(TestConstants.getFile(base, "oaipmh.xsd"));
        files.add(TestConstants.getFile(base, "xlink.xsd"));
        files.add(TestConstants.getFile(base, "XMLSchema.xsd"));
        files.add(TestConstants.getFile(base, "xml.xsd"));
        files.add(TestConstants.getFile(base, "simpledc20021212.xsd"));
        try {
            files.add(serializationService.generateSchema());
        } catch (Throwable t) {
            logger.warn("{}", t);
        }
    }

    private Validator setupValidator() throws FileNotFoundException, SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        List<Source> sources = new ArrayList<>();
        for (File file : files) {
            sources.add(new StreamSource(file));
        }
        factory.setResourceResolver(new TdarSchemaResourceResolver());
        Schema newSchema = factory.newSchema(sources.toArray(new Source[0]));
        v = newSchema.newValidator();
        return v;
    }

    public Validator getValidator() {
        return v;
    }

    public List<?> getInstanceErrors(StreamSource is) {
        List<Exception> errors = new ArrayList<>();
        v.setErrorHandler(new TdarJaxbSchemaErrorHandler(errors));
        try {
            v.validate(is);
        } catch (SAXException | IOException e) {
            errors.add(e);
        }
        return errors;
    }
}
