package com.timepath.launcher;

import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import org.w3c.dom.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * @author TimePath
 */
public class RepositoryParser {

    public static Repository parse(Node version) {
        if(version == null) {
            return null;
        }
        Repository r = new Repository();
        r.packages = new LinkedList<>();
        List<Node> meta = XMLUtils.getElements("meta", version);
        r.name = XMLUtils.getAttribute(meta.get(0), "name");
        String[] nodes = { "self", "libs", "programs" };
        for(String s1 : nodes) {
            List<Node> programs = XMLUtils.getElements(s1 + "/entry", version);
            for(Node entry : programs) {
                Program p = new Program();
                p.title = XMLUtils.getAttribute(entry, "name");
                String depends = XMLUtils.getAttribute(entry, "depends");
                if(depends != null) {
                    String[] dependencies = depends.split(",");
                    for(String s : dependencies) {
                        p.depends.add(r.libs.get(s.trim()));
                    }
                }
                p.fileName = XMLUtils.getAttribute(entry, "file");
                Node java = XMLUtils.last(XMLUtils.getElements("java", entry));
                if(java != null) {
                    p.main = XMLUtils.getAttribute(java, "main");
                    p.args = Utils.argParse(XMLUtils.getAttribute(java, "args"));
                    String daemon = XMLUtils.getAttribute(java, "daemon");
                    if(daemon != null) {
                        p.daemon = Boolean.parseBoolean(daemon);
                    }
                }
                Node news = XMLUtils.last(XMLUtils.getElements("newsfeed", entry));
                if(news != null) {
                    p.newsfeedURL = XMLUtils.getAttribute(news, "url");
                }
                p.downloads = getDownloads(entry);
                if(s1.equals(nodes[0])) {
                    for(PackageFile file : p.downloads) {
                        file.fileName = JARUtils.UPDATE_NAME;
                    }
                    p.setSelf(true);
                    r.self = p;
                    r.packages.add(p);
                } else if(s1.equals(nodes[1])) {
                    r.libs.put(p.title, p);
                } else if(s1.equals(nodes[2])) {
                    r.packages.add(p);
                }
            }
        }
        return r;
    }

    private static List<PackageFile> getDownloads(Node entry) {
        List<PackageFile> downloads = new LinkedList<>();
        // downloadURL
        for(Node node : XMLUtils.getElements("download", entry)) {
            Node checksum = XMLUtils.last(XMLUtils.getElements("checksum", entry));
            String dlu = XMLUtils.getAttribute(node, "url");
            if(dlu == null) {
                continue;
            }
            String csu = null;
            if(checksum != null) {
                csu = XMLUtils.getAttribute(checksum, "url");
            }
            PackageFile file = new PackageFile(dlu, csu);
            file.nested = getDownloads(node);
            downloads.add(file);
        }
        return downloads;
    }
}
