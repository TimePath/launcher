/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.timepath.launcher.ui.swing;

import com.timepath.launcher.util.Utils;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class ThemeSelector extends JComboBox<String> {

    private static final Logger LOG = Logger.getLogger(ThemeSelector.class.getName());

    public ThemeSelector() {
        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        this.setModel(model);

        String currentLafClass = UIManager.getLookAndFeel().getClass().getName();
        for(UIManager.LookAndFeelInfo lafInfo : UIManager.getInstalledLookAndFeels()) {
            try {
                Class.forName(lafInfo.getClassName());
            } catch(ClassNotFoundException ex) {
                continue; // Registered but not found on classpath
            }
            String name = lafInfo.getName();
            model.addElement(name);
            if(lafInfo.getClassName().equals(currentLafClass)) {
                model.setSelectedItem(name);
            }
        }

        this.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String target = (String) model.getSelectedItem();
                for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if(target.equals(info.getName())) {
                        LOG.log(Level.INFO, "Setting L&F: {0}", info.getClassName());
                        try {
                            String usrTheme = info.getClassName();
                            UIManager.setLookAndFeel(usrTheme);
                            for(Window w : Window.getWindows()) {
                                SwingUtilities.updateComponentTreeUI(w);
                            }
                            Utils.settings.put("laf", usrTheme);
                            return;
                        } catch(InstantiationException | IllegalAccessException |
                                UnsupportedLookAndFeelException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        } catch(ClassNotFoundException ex) {
                            LOG.log(Level.WARNING, "Unable to load user L&F\n{0}", ex);
                        }
                    }
                }
            }
        });
    }

}
