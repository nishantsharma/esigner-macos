package com.eproc.mac;

import java.awt.GraphicsEnvironment;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/** Asks the user for the token PIN. SunMSCAPI never needed one; PKCS#11 does. */
final class PinDialog {

    private PinDialog() {
    }

    static char[] ask(String tokenLabel) {
        // Escape hatch for headless runs and for testing.
        String env = System.getenv("ESIGNER_PIN");
        if (env != null && !env.isEmpty()) {
            return env.toCharArray();
        }
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        final char[][] out = new char[1][];
        Runnable prompt = () -> {
            JPasswordField field = new JPasswordField(18);
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            String who = (tokenLabel == null || tokenLabel.isEmpty())
                    ? "your DSC token"
                    : tokenLabel;
            panel.add(new JLabel("<html>Enter the PIN for <b>" + escape(who)
                    + "</b><br><small>Wrong PINs count against the token's retry "
                    + "limit.</small></html>"), BorderLayout.NORTH);
            panel.add(field, BorderLayout.CENTER);

            JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.OK_CANCEL_OPTION);
            JDialog dialog = pane.createDialog("eProcurement (eSigner)");
            dialog.setAlwaysOnTop(true);
            // The password field must own focus when the dialog appears.
            dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    field.requestFocusInWindow();
                }
            });
            dialog.setVisible(true);
            dialog.dispose();

            Object value = pane.getValue();
            if (value instanceof Integer && (Integer) value == JOptionPane.OK_OPTION) {
                char[] pin = field.getPassword();
                out[0] = (pin.length == 0) ? null : pin;
            }
        };

        try {
            if (SwingUtilities.isEventDispatchThread()) {
                prompt.run();
            } else {
                SwingUtilities.invokeAndWait(prompt);
            }
        } catch (Exception e) {
            return null;
        }
        return out[0];
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
