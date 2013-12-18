package com.reichart.andreas.rasp.bot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class JFrameGyroDiagram extends JFrame {

    private PanelGraphics graphicsPane;
    private JTextField textFieldXAxis;
    private JTextField textFieldYAxis;
    private JTextField textFieldZAxis;
    private Map<GyroAxes, JTextField> textFields;

    /**
     * Create the frame.
     */
    public JFrameGyroDiagram() {
	initGUI();
	initMap();
    }

    /**
     * Init the textfields map.
     */
    private void initMap() {
	textFields = new HashMap<GyroAxes, JTextField>(3);
	textFields.put(GyroAxes.GYRO_X, textFieldXAxis);
	textFields.put(GyroAxes.GYRO_Y, textFieldYAxis);
	textFields.put(GyroAxes.GYRO_Z, textFieldZAxis);
    }

    private void initGUI() {
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	setBounds(100, 100, 651, 438);
	graphicsPane = new PanelGraphics();
	graphicsPane.setBorder(new EmptyBorder(5, 5, 5, 5));
	graphicsPane.setLayout(new BorderLayout(0, 0));
	setContentPane(graphicsPane);

	JPanel panelGraphics = new JPanel();
	graphicsPane.add(panelGraphics, BorderLayout.CENTER);

	JPanel ValuePanel = new JPanel();
	graphicsPane.add(ValuePanel, BorderLayout.SOUTH);
	GridBagLayout gbl_ValuePanel = new GridBagLayout();
	gbl_ValuePanel.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0 };
	gbl_ValuePanel.rowHeights = new int[] { 0, 0 };
	gbl_ValuePanel.columnWeights = new double[] { 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, Double.MIN_VALUE };
	gbl_ValuePanel.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
	ValuePanel.setLayout(gbl_ValuePanel);

	JLabel lblXaxis = new JLabel("X-axis");
	GridBagConstraints gbc_lblXaxis = new GridBagConstraints();
	gbc_lblXaxis.insets = new Insets(0, 0, 0, 5);
	gbc_lblXaxis.gridx = 0;
	gbc_lblXaxis.gridy = 0;
	ValuePanel.add(lblXaxis, gbc_lblXaxis);

	textFieldXAxis = new JTextField();
	GridBagConstraints gbc_textFieldXAxis = new GridBagConstraints();
	gbc_textFieldXAxis.insets = new Insets(0, 0, 0, 5);
	gbc_textFieldXAxis.fill = GridBagConstraints.HORIZONTAL;
	gbc_textFieldXAxis.gridx = 1;
	gbc_textFieldXAxis.gridy = 0;
	ValuePanel.add(textFieldXAxis, gbc_textFieldXAxis);
	textFieldXAxis.setColumns(10);

	JLabel lblYaxis = new JLabel("Y-axis");
	GridBagConstraints gbc_lblYaxis = new GridBagConstraints();
	gbc_lblYaxis.anchor = GridBagConstraints.EAST;
	gbc_lblYaxis.insets = new Insets(0, 0, 0, 5);
	gbc_lblYaxis.gridx = 2;
	gbc_lblYaxis.gridy = 0;
	ValuePanel.add(lblYaxis, gbc_lblYaxis);

	textFieldYAxis = new JTextField();
	GridBagConstraints gbc_textFieldYAxis = new GridBagConstraints();
	gbc_textFieldYAxis.insets = new Insets(0, 0, 0, 5);
	gbc_textFieldYAxis.fill = GridBagConstraints.HORIZONTAL;
	gbc_textFieldYAxis.gridx = 3;
	gbc_textFieldYAxis.gridy = 0;
	ValuePanel.add(textFieldYAxis, gbc_textFieldYAxis);
	textFieldYAxis.setColumns(10);

	JLabel lblZaxis = new JLabel("Z-axis");
	GridBagConstraints gbc_lblZaxis = new GridBagConstraints();
	gbc_lblZaxis.anchor = GridBagConstraints.EAST;
	gbc_lblZaxis.insets = new Insets(0, 0, 0, 5);
	gbc_lblZaxis.gridx = 4;
	gbc_lblZaxis.gridy = 0;
	ValuePanel.add(lblZaxis, gbc_lblZaxis);

	textFieldZAxis = new JTextField();
	GridBagConstraints gbc_textFieldZAxis = new GridBagConstraints();
	gbc_textFieldZAxis.fill = GridBagConstraints.HORIZONTAL;
	gbc_textFieldZAxis.gridx = 5;
	gbc_textFieldZAxis.gridy = 0;
	ValuePanel.add(textFieldZAxis, gbc_textFieldZAxis);
	textFieldZAxis.setColumns(10);
    }

    /**
     * Add new values to the visualization.
     * 
     * @param angleMap
     */
    void addValues(Map<GyroAxes, Float> angleMap) {
	setTextFieldsValues(angleMap);
	graphicsPane.setData(angleMap);
    }

    private void setTextFieldsValues(Map<GyroAxes, Float> angleMap) {
	for (Map.Entry<GyroAxes, Float> entry : angleMap.entrySet()) {
	    textFields.get(entry.getKey()).setText(String.valueOf(entry.getValue()));
	}
    }

    private class PanelGraphics extends JPanel {

	private Map<GyroAxes, Float> map = new HashMap<>(3);
	private final static int BAR_WIDTH = 60;
	private Map<GyroAxes, Color> barColors = new HashMap<GyroAxes, Color>(3);

	PanelGraphics() {
	    barColors.put(GyroAxes.GYRO_X, Color.RED);
	    barColors.put(GyroAxes.GYRO_Y, Color.GREEN);
	    barColors.put(GyroAxes.GYRO_Z, Color.BLUE);
	    setDoubleBuffered(true);
	}

	@Override
	public void paint(Graphics g) {
	    super.paint(g);

	    Graphics2D g2 = (Graphics2D) g;

	    int centerY = this.getHeight() / 2;
	    int quartWidth = this.getWidth() / 4;
	    int currentX = quartWidth;
	    int barheight;

	    for (Map.Entry<GyroAxes, Float> entry : map.entrySet()) {
		g2.drawString(entry.getKey().toString(), currentX - BAR_WIDTH / 2, 10);
		barheight = (int) (((float) centerY - 10f) * (entry.getValue() / 180f));
		int barTop = (barheight > 0) ? centerY - barheight : centerY;
		// int barBottom = (barheight > 0) ? centerY : barheight;
		g2.setColor(barColors.get(entry.getKey()));
		g2.fillRect(currentX, barTop, BAR_WIDTH / 2, Math.abs(barheight));
		currentX += quartWidth;
	    }
	    g2.dispose();
	    g2 = null;
	}

	@Override
	public void paintComponent(Graphics g) {
	    super.paintComponent(g);
	}

	void setData(Map<GyroAxes, Float> angleMap) {
	    this.map = angleMap;
	    repaint();
	}
    }

}
