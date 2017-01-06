package tca;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

public abstract class Chart {
	
	protected String title;
	protected boolean showLegend = true;
	protected boolean showLabels = false;
	
	protected Chart(String title){
		this.title = title;
	}

	public abstract JFreeChart getChart();
	
	public boolean showLegendEnabled(){
		return showLegend;
	}
	
	public void enableShowLegend(boolean showLegend){
		this.showLegend = showLegend;
	}
	
	public boolean showLabelsEnabled(){
		return showLabels;
	}
	
	public void enableShowLabels(boolean showLabels){
		this.showLabels = showLabels;
	}
	
	public void show(){
		SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new JFrame("Time Complexity Analyzer");
                frame.setSize(600, 400);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
                ChartPanel chartPanel = new ChartPanel(getChart());
                frame.getContentPane().add(chartPanel);
            }
        });
	}

}
