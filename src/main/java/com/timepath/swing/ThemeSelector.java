/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.timepath.swing;

import com.timepath.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class ThemeSelector extends JComboBox<String> {

    private static final Logger LOG = Logger.getLogger(ThemeSelector.class.getName());

    public ThemeSelector() {
        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        setModel(model);
        String currentLafClass = UIManager.getLookAndFeel().getClass().getName();
        for (UIManager.LookAndFeelInfo lafInfo : UIManager.getInstalledLookAndFeels()) {
            try {
                Class.forName(lafInfo.getClassName());
            } catch (ClassNotFoundException ignored) {
                continue; // Registered but not found on classpath
            }
            String name = lafInfo.getName();
            model.addElement(name);
            if (lafInfo.getClassName().equals(currentLafClass)) {
                model.setSelectedItem(name);
            }
        }
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                String target = (String) model.getSelectedItem();
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (target.equals(info.getName())) {
                        LOG.log(Level.INFO, "Setting L&F: {0}", info.getClassName());
                        try {
                            String usrTheme = info.getClassName();
                            UIManager.setLookAndFeel(usrTheme);
                            for (Window w : Window.getWindows()) { // TODO: Instrumentation to access detached components
                                SwingUtilities.updateComponentTreeUI(w);
                            }
                            Utils.SETTINGS.put("laf", usrTheme);
                            return;
                        } catch (InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                            LOG.log(Level.SEVERE, null, e);
                        } catch (ClassNotFoundException e) {
                            LOG.log(Level.WARNING, "Unable to load user L&F", e);
                        }
                    }
                }
            }
        });
    }
}
