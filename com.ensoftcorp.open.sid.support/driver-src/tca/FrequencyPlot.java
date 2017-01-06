package tca;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;

public class FrequencyPlot extends Chart {

	double[] data;
	
	public FrequencyPlot(String title, double[] data) {
		super(title);
		this.data = data;
	}

	@Override
	public JFreeChart getChart() {
		XYSeriesCollection dataset = new XYSeriesCollection();
		XYSeries series = new XYSeries("Measurements");
		for(int i=0; i<data.length; i++){
			series.add(new XYDataItem(i, data[i]));
		}
		dataset.addSeries(series);

		JFreeChart chart = ChartFactory.createScatterPlot(title, // title
														  "Input", // x-axis label
														  "Frequency",  // y-axis label
														  dataset, // data
														  PlotOrientation.VERTICAL, // orientation
														  showLegend, // create legend
														  true, // generate tooltips
														  false); // generate urls
		
		chart.setBackgroundPaint(java.awt.Color.white);
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(java.awt.Color.lightGray);
		plot.setDomainGridlinePaint(java.awt.Color.white);
		plot.setRangeGridlinePaint(java.awt.Color.white);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);
		
		return chart;
	}

}
