package com.timepath.launcher.data

import com.timepath.StringUtils
import com.timepath.XMLUtils
import com.timepath.launcher.LauncherUtils
import com.timepath.maven.Package
import org.w3c.dom.Node
import org.xml.sax.SAXException

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.dom.DOMSource
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.text.MessageFormat
import java.util.Collections
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger
import java.net.URI

/**
 * A repository contains a list of multiple {@code Package}s and their {@code Program}s
 *
 * @author TimePath
 */
public class Repository private() {
    /**
     * URL to the index file
     */
    public var location: String? = null
        private set
    /**
     * The package representing this repository. Mostly only relevant to the main repository so that the main launcher
     * has a way of updating itself
     */
    public var self: com.timepath.maven.Package? = null
        private set
    /**
     * The name of this repository
     */
    private var name: String? = null
    /**
     * A list of all program entry points
     */
    private var executions: MutableList<Program>? = null
    private var enabled: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this == other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as Repository

        if (location != that.location) return false

        return true
    }

    override fun hashCode(): Int {
        return location!!.hashCode()
    }

    /**
     * @return the executions
     */
    public fun getExecutions(): List<Program> {
        return if (enabled) Collections.unmodifiableList<Program>(executions) else listOf<Program>()
    }

    override fun toString(): String {
        return MessageFormat.format("{0} ({1})", name, location)
    }

    /**
     * @return the name
     */
    public fun getName(): String {
        return if (name == null) location!! else name!!
    }

    public fun isEnabled(): Boolean {
        return enabled
    }

    public fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    class object {
        private val LOG = Logger.getLogger(javaClass<Repository>().getName())

        /**
         * Constructs a repository from a compatible node within a larger document
         *
         * @param location
         * @return
         */
        public fun fromIndex(location: String): Repository? {
            val page = try {
                URI(location).toURL().readText()
            } catch (ignored: IOException) {
                return null
            }
            val data = page.toByteArray()
            val r = parse(findCompatible(ByteArrayInputStream(data)))
            r.location = location
            return r
        }

        /**
         * Constructs a repository from a root node
         *
         * @param root
         * @return
         */
        private fun parse(root: Node?): Repository {
            if (root == null) throw IllegalArgumentException("The root node must not be null")
            val r = Repository()
            r.name = XMLUtils.get(root, "name")
            r.self = Package.parse(root, null)
            if (r.self != null) r.self!!.setSelf(true)
            r.executions = LinkedList<Program>()
            for (entry in XMLUtils.getElements(root, "programs/program")) {
                val pkg = Package.parse(entry, null)
                // extended format with execution data
                for (execution in XMLUtils.getElements(entry, "executions/execution")) {
                    val cfg = XMLUtils.last<Node>(XMLUtils.getElements(execution, "configuration"))
                    val p = Program(pkg!!, XMLUtils.get(execution, "name"), XMLUtils.get(execution, "url"), XMLUtils.get(cfg, "main"), StringUtils.argParse(XMLUtils.get(cfg, "args")))
                    r.executions!!.add(p)
                    val daemonStr = XMLUtils.get(cfg, "daemon")
                    if (daemonStr != null) p.setDaemon(java.lang.Boolean.parseBoolean(daemonStr))
                }
            }
            return r
        }

        /**
         * @return a compatible configuration node
         */
        private fun findCompatible(`is`: InputStream): Node? {
            try {
                val docBuilderFactory = DocumentBuilderFactory.newInstance()
                val docBuilder = docBuilderFactory.newDocumentBuilder()
                val doc = docBuilder.parse(BufferedInputStream(`is`))
                val root = XMLUtils.getElements(doc, "root")[0]
                var version: Node? = null
                var iter: Node? = null
                val versions = root.getChildNodes()
                run {
                    var i = 0
                    while (i < versions.getLength()) {
                        if (iter != null && iter!!.hasAttributes()) {
                            val attributes = iter!!.getAttributes()
                            val versionAttribute = attributes.getNamedItem("version")
                            if (versionAttribute != null) {
                                val v = versionAttribute.getNodeValue()
                                if (v != null) {
                                    try {
                                        if (LauncherUtils.DEBUG || (LauncherUtils.CURRENT_VERSION >= java.lang.Long.parseLong(v))) {
                                            version = iter
                                        }
                                    } catch (ignored: NumberFormatException) {
                                    }
                                }
                            }
                        }
                        iter = versions.item(i++)
                    }
                }
                LOG.log(Level.FINE, "\n{0}", XMLUtils.pprint(DOMSource(version), 2))
                return version
            } catch (ex: IOException) {
                LOG.log(Level.SEVERE, null, ex)
            } catch (ex: ParserConfigurationException) {
                LOG.log(Level.SEVERE, null, ex)
            } catch (ex: SAXException) {
                LOG.log(Level.SEVERE, null, ex)
            }

            return null
        }
    }
}
