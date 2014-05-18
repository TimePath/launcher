package com.timepath.launcher.ui.web;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses JAXP to transform xml with xsl
 *
 * @author TimePath
 */
public class Transform {

    private static final Logger LOG = Logger.getLogger(Transform.class.getName());

    private Transform() {}

    public static void main(String[] argv) throws IOException, MalformedURLException {
        if(argv.length != 2) {
            System.err.println("Usage: java " + Transform.class.getName() + " xsl xml");
            System.exit(1);
        }
        String str = transform(new URL(argv[0]).openStream(), new URL(argv[1]).openStream());
        System.out.println(str);
    }

    @SuppressWarnings("MethodNamesDifferingOnlyByCase")
    public static String transform(InputStream xsl, InputStream xml) {
        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Source xslDoc = new StreamSource(xsl);
            Source xmlDoc = new StreamSource(xml);
            Transformer trasform = tFactory.newTransformer(xslDoc);
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream(10240);
            Result result = new StreamResult(byteArray);
            trasform.transform(xmlDoc, result);
            return byteArray.toString();
        } catch(TransformerException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
