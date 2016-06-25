package tca;

import java.text.DecimalFormat;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;

import tca.Measurements.Measurement;

public class RegressionPlotChart extends Chart {
	
	Measurements measurements;

	public RegressionPlotChart(String title, Measurements measurements) {
		super(title);
		this.measurements = measurements;
	}

	@Override
	public JFreeChart getChart() {
		
		int n = measurements.getNumMeasurements();
		double[] x = new double[n];
		double[] y = new double[n];
		
		for(int i=0; i<n; i++){
			x[i] = measurements.getMeasurements().get(i).getLogSize();
			y[i] = measurements.getMeasurements().get(i).getLogValue();
		}
		
		double[] result = regress(x, y, n);
		double intercept = result[0];
		double slope = result[1];
		double r2 = result[2];
		
		XYSeriesCollection dataset = new XYSeriesCollection();
		XYSeries series = new XYSeries("Measurements");
		for(Measurement measurement : measurements.getMeasurements()){
			series.add(new XYDataItem(measurement.getLogSize(), measurement.getLogValue()));
		}
		dataset.addSeries(series);
		
		DecimalFormat decimalFormat = new DecimalFormat("0.00");
		
		JFreeChart chart = ChartFactory.createScatterPlot(title + ", R2=" + decimalFormat.format(r2), // title
														  "Log(Workload Size)", // x-axis label
														  "Log(Measurement)",  // y-axis label
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
		
		
//		// lazy points...not working?
//		double x1 = 0;
//		double y1 = intercept;
//		double x2 = 1;
//		double y2 = slope + intercept;
		
		// add the trendline
		double xlb = 0;
		double xub = plot.getDomainAxis().getRange().getUpperBound();
		double ylb = 0;
		double yub = plot.getRangeAxis().getRange().getUpperBound();
		double x1 = getX(ylb, slope, intercept);
		double y1 = getY(xlb, slope, intercept);
		double x2 = getX(yub, slope, intercept);
		double y2 = getY(xub, slope, intercept);
		XYLineAnnotation annotation = new XYLineAnnotation(x1, y1, x2, y2);
		annotation.setToolTipText("y=" + decimalFormat.format(slope) + "*x + " + decimalFormat.format(intercept));
		plot.addAnnotation(annotation);
		
		return chart;
	}
	
	// y=mx+b where m is the slope and b is the y-intercept
	private static double getY(double x, double slope, double intercept){
		double y = (slope * x) + intercept;
		return y;
	}
	
	// x = (y-b)/m where m is the slope and b is the y-intercept
	private static double getX(double y, double slope, double intercept){
		double x = (y-intercept)/slope;
		return x;
	}
	
	private static double[] regress(double[] x, double[] y, int n) {
		double[] res = new double[3];
		// first pass: read in data, compute xbar and ybar
		double sumx = 0, sumy = 0; // , sumx2 = 0.0; used only for error
									// analysis
		for (int i = 0; i < n; i++) {
			sumx += x[i];
			sumy += y[i];
			// sumx2 += x[i]*x[i];
		}
		double xbar = sumx / n;
		double ybar = sumy / n;

		// second pass: compute summary statistics
		double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
		for (int i = 0; i < n; i++) {
			xxbar += (x[i] - xbar) * (x[i] - xbar);
			yybar += (y[i] - ybar) * (y[i] - ybar);
			xybar += (x[i] - xbar) * (y[i] - ybar);
		}
		double beta1 = xybar / xxbar;
		double beta0 = ybar - beta1 * xbar;
		res[0] = beta0;
		res[1] = beta1;

		double ssr = 0.0; // regression sum of squares
		for (int i = 0; i < n; i++) {
			double fit = beta1 * x[i] + beta0;
			// rss += (fit - y[i]) * (fit - y[i]);
			ssr += (fit - ybar) * (fit - ybar);
		}
		double R2 = ssr / yybar;
		res[2] = R2;
		return res;
	}

}
