package com.timepath;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author timepath
 */
public class Launcher extends javax.swing.JFrame {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    private static String getAttribute(Node n, String key) {
        Element p = (Element) n;
        String val = p.getAttributeNode(key) != null ? p.getAttributeNode(key).getValue().trim() : null;
        NodeList nodes = p.getElementsByTagName(key);
        if(p.getNodeType() == Node.ELEMENT_NODE && nodes.getLength() > 0) {
            Element lastElement = (Element) nodes.item(nodes.getLength() - 1);
            NodeList children = lastElement.getChildNodes();
            val = ((Node) children.item(children.getLength() - 1)).getNodeValue();
        }
        return val;
    }

    private DefaultListModel/*
             * <Project>
             */ listModel;

    /**
     * Creates new form Launcher
     */
    public Launcher() {
        initComponents();

        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(e.getValueIsAdjusting()) {
                    return;
                }
                Project p = (Project) list.getSelectedValue();
                loadPageForProject(p);
            }
        });

        parseXML();
    }

    private void loadPageForProject(final Project p) {
        if(p.url != null) {
            System.out.println(p.url);

            Launcher.this.jEditorPane1.setContentType("text/html");
            Launcher.this.jEditorPane1.setText("");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String s = p.url;
                        StringBuilder sb = new StringBuilder();

                        URL u = new URL(s);
                        URLConnection c = u.openConnection();
                        InputStream is = c.getInputStream();
                        BufferedReader r = new BufferedReader(new InputStreamReader(is));
                        String line;
                        while((line = r.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        r.close();
                        Launcher.this.jEditorPane1.setText(sb.toString());
                    } catch(IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        }
    }

    private void runProject(Project p) {
        try {
            final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

            final ArrayList<String> cmd = new ArrayList<String>();
            cmd.add(javaBin);
            cmd.add("-jar");
            cmd.add(p.jar);
            String[] exec = new String[cmd.size()];
            cmd.toArray(exec);
            LOG.log(Level.INFO, "Invoking other: {0}", Arrays.toString(exec));
            final ProcessBuilder process = new ProcessBuilder(exec);
            process.start();
            System.exit(0);
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        list = new javax.swing.JList();
        jScrollPane2 = new javax.swing.JScrollPane();
        jEditorPane1 = new javax.swing.JEditorPane();
        jButton1 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(list);

        jEditorPane1.setEditable(false);
        jEditorPane1.setContentType("text/html"); // NOI18N
        jEditorPane1.setText("");
        jScrollPane2.setViewportView(jEditorPane1);

        jButton1.setText("Launch");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 253, Short.MAX_VALUE)
                        .addComponent(jButton1)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton1)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        runProject((Project) list.getSelectedValue());
    }//GEN-LAST:event_jButton1ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /*
         * Set the Nimbus look and feel
         */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /*
         * If Nimbus (introduced in Java SE 6) is not available, stay with the default look and
         * feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for(javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch(ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Launcher.class.getName()).log(
                    java.util.logging.Level.SEVERE, null, ex);
        } catch(InstantiationException ex) {
            java.util.logging.Logger.getLogger(Launcher.class.getName()).log(
                    java.util.logging.Level.SEVERE, null, ex);
        } catch(IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Launcher.class.getName()).log(
                    java.util.logging.Level.SEVERE, null, ex);
        } catch(javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Launcher.class.getName()).log(
                    java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /*
         * Create and display the form
         */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                Launcher l = new Launcher();
                l.setLocationRelativeTo(null);
                l.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JEditorPane jEditorPane1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList list;
    // End of variables declaration//GEN-END:variables

    private void parseXML() {
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File("projects.xml"));
            doc.getDocumentElement().normalize();

            NodeList programs = doc.getElementsByTagName("program");

            listModel = new DefaultListModel/*
                     * <Project>
                     */();
            this.list.setModel(listModel);
            for(int i = 0; i < programs.getLength(); i++) {
                Node program = programs.item(i);
                Project p = new Project();
                p.name = getAttribute(program, "name");
                p.url = getAttribute(program, "url");
                p.jar = getAttribute(program, "jar");
                listModel.addElement(p);
            }
        } catch(SAXParseException err) {
            System.out.println(
                    "** Parsing error" + ", line " + err.getLineNumber() + ", uri " + err.getSystemId());
            System.out.println(" " + err.getMessage());
        } catch(SAXException e) {
            Exception x = e.getException();
            ((x == null) ? e : x).printStackTrace();
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }

    private class Project {

        private String name;

        private String url;

        private String jar;

        @Override
        public String toString() {
            return name;
        }

    }

}
