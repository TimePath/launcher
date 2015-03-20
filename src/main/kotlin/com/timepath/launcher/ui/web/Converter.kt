package com.timepath.launcher.ui.web

import com.timepath.launcher.data.Program
import com.timepath.launcher.data.Repository
import com.timepath.maven.UpdateChecker
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.Source
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * @author TimePath
 */
object Converter {

    val transformerFactory = TransformerFactory.newInstance()

    /**
     * Uses JAXP to transform xml with xsl
     *
     * @param xslDoc
     * @param xmlDoc
     * @return
     * @throws javax.xml.transform.TransformerException
     */
    throws(javaClass<TransformerException>())
    public fun transform(xslDoc: Source, xmlDoc: Source): String {
        val byteArray = ByteArrayOutputStream(10240)
        val transformer = transformerFactory.newTransformer(xslDoc)
        val outputTarget = StreamResult(byteArray)
        transformer.transform(xmlDoc, outputTarget)
        return byteArray.toString()
    }

    throws(javaClass<ParserConfigurationException>())
    public fun serialize(repos: Iterable<Repository>): Source {
        val programs = LinkedList<Program>()
        for (repo in repos) {
            programs.addAll(repo.getExecutions())
        }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("root")
        val rootPrograms = root.appendChild(document.createElement("programs"))
        val rootLibs = root.appendChild(document.createElement("libs"))
        for (p in programs) {
            val elemProgram = document.createElement("entry")
            run {
                elemProgram.setAttribute("appid", p.id.toString())
                elemProgram.setAttribute("name", p.title)
                elemProgram.setAttribute("saved", p.isStarred().toString())
                val rootDeps = elemProgram.appendChild(document.createElement("depends"))
                run {
                    for (dep in p.`package`.getDownloads()) {
                        val elemPackage = document.createElement("entry")
                        run {
                            elemPackage.setAttribute("name", dep.name)
                            val elemDownload = document.createElement("download")
                            run {
                                elemDownload.setAttribute("progress", (when {
                                    dep.progress == 0L -> "0"
                                    else -> ((dep.progress.toDouble() * 100.0) / dep.size.toDouble()).toString()
                                }))
                                elemDownload.setAttribute("url", UpdateChecker.getDownloadURL(dep))
                            }
                            elemPackage.appendChild(elemDownload)
                        }
                        rootDeps.appendChild(elemPackage)
                    }
                }
                elemProgram.appendChild(rootDeps)
            }
            rootPrograms.appendChild(elemProgram)
            rootLibs.appendChild(elemProgram.cloneNode(true))
        }
        return DOMSource(root)
    }
}
