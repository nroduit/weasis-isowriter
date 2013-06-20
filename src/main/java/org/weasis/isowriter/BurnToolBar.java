package org.weasis.isowriter;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.util.WtoolBar;

public class BurnToolBar<E extends ImageElement> extends WtoolBar {
    private final Logger LOGGER = LoggerFactory.getLogger(BurnToolBar.class);

    // Permanent preferences of this bundle
    public static final BundlePreferences PREFERENCES = new BundlePreferences();

    public BurnToolBar() {
        super("CD/DVD Burning Bar", TYPE.tool);

        final JButton helpButton = new JButton();
        helpButton.setToolTipText("User Guide");
        helpButton.setIcon(new ImageIcon(BurnToolBar.class.getResource("/icon/32x32/help-browser.png"))); //$NON-NLS-1$
        helpButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof Component) {
                    URL url;
                    try {
                        url = new URL("http://www.dcm4che.org/confluence/display/WEA/User+Guide"); //$NON-NLS-1$
                        JMVUtils.OpenInDefaultBrowser((Component) e.getSource(), url);
                    } catch (MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        add(helpButton);
    }

    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("Cine toolbar is activate");
        // Initialize preferences
        PREFERENCES.init(context.getBundleContext());
        Preferences prefs = PREFERENCES.getDefaultPreferences();
        if (prefs != null) {
            this.setEnabled(prefs.getBoolean("toolbar.visible", true));
        }
    }

    protected void deactivate(ComponentContext context) {
        LOGGER.info("Cine toolbar is deactivate");
        // Save preferences
        Preferences prefs = PREFERENCES.getDefaultPreferences();
        if (prefs != null) {
            BundlePreferences.putBooleanPreferences(prefs, "toolbar.visible", this.isEnabled());
        }
        PREFERENCES.close();
    }
}
