package com.timepath.launcher.ui.web;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Uses JAXP to transform xml with xsl
 * <p>
 * @author TimePath
 */
public class Transform {

    private static final Logger LOG = Logger.getLogger(Transform.class.getName());

    public static void main(String[] argv) throws Exception {
        if(argv.length != 2) {
            System.err.println("Usage: java " + Transform.class.getName() + " xsl xml");
            System.exit(1);
        }
        String str = transform(new URL(argv[0]).openStream(), new URL(argv[1]).openStream());
        System.out.println(str);
    }

    public static String transform(InputStream xsl, InputStream xml) {
        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();

            StreamSource xslDoc = new StreamSource(xsl);
            StreamSource xmlDoc = new StreamSource(xml);

            Transformer trasform = tFactory.newTransformer(xslDoc);
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream(10240);
            StreamResult result = new StreamResult(byteArray);
            trasform.transform(xmlDoc, result);
            return byteArray.toString();
        } catch(TransformerException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

}
