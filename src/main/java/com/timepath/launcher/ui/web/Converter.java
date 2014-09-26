package com.timepath.launcher.ui.web;

import com.timepath.launcher.data.Program;
import com.timepath.launcher.data.Repository;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.LinkedList;

/**
 * @author TimePath
 */
class Converter {

    static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    /**
     * Uses JAXP to transform xml with xsl
     *
     * @param xslDoc
     * @param xmlDoc
     * @return
     * @throws javax.xml.transform.TransformerException
     */
    public static String transform(Source xslDoc, Source xmlDoc) throws TransformerException {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream(10240);
        Transformer transformer = transformerFactory.newTransformer(xslDoc);
        StreamResult outputTarget = new StreamResult(byteArray);
        transformer.transform(xmlDoc, outputTarget);
        return byteArray.toString();
    }

    public static Source serialize(Iterable<Repository> repos) throws ParserConfigurationException {
        Collection<Program> programs = new LinkedList<>();
        for (Repository repo : repos) {
            programs.addAll(repo.getExecutions());
        }
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Node root = document.createElement("root");
        Node rootPrograms = root.appendChild(document.createElement("programs"));
        Node rootLibs = root.appendChild(document.createElement("libs"));
        for (Program p : programs) {
            Element elemProgram = document.createElement("entry");
            {
                elemProgram.setAttribute("appid", String.valueOf(p.getId()));
                elemProgram.setAttribute("name", p.getTitle());
                elemProgram.setAttribute("saved", String.valueOf(p.isStarred()));
                Node rootDeps = elemProgram.appendChild(document.createElement("depends"));
                {
                    for (Package dep : p.getPackage().getDownloads()) {
                        Element elemPackage = document.createElement("entry");
                        {
                            elemPackage.setAttribute("name", dep.getName());
                            Element elemDownload = document.createElement("download");
                            {

                                elemDownload.setAttribute("progress", String.valueOf(dep.progress == 0
                                        ? 0
                                        : (dep.progress * 100.0) / dep.size));
                                elemDownload.setAttribute("url", UpdateChecker.getDownloadURL(dep));
                            }
                            elemPackage.appendChild(elemDownload);
                        }
                        rootDeps.appendChild(elemPackage);
                    }
                }
                elemProgram.appendChild(rootDeps);
            }
            rootPrograms.appendChild(elemProgram);
            rootLibs.appendChild(elemProgram.cloneNode(true));
        }
        return new DOMSource(root);
    }

}
