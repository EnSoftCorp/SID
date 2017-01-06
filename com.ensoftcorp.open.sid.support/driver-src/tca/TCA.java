package tca;

import java.io.IOException;
import java.util.HashMap;

import tca.instrumentation.TCA_Counter;
import tca.instrumentation.TCA_Timer;

public class TCA {

	public static void plotCounterRegression(final String title, final int TOTAL_WORK_TASKS) throws IOException, InterruptedException {
		Measurements measurements = new Measurements();
		for (int i = 1; i <= TOTAL_WORK_TASKS; i++) {
			@SuppressWarnings("unchecked")
			HashMap<String, Long> counters = TCA_Counter.getInstance().getMeasurementForSize(i);
			long sum = 0;
			for (Long l : counters.values()) {
				sum += l;
			}
			measurements.add(i, sum);
		}
		
		RegressionPlotChart scatterPlot = new RegressionPlotChart(title, measurements);
		scatterPlot.show();
	}
	
	public static void plotTimerRegression(final String title, final int TOTAL_WORK_TASKS) throws IOException, InterruptedException {
		Measurements measurements = new Measurements();
		for (int i = 1; i <= TOTAL_WORK_TASKS; i++) {
			@SuppressWarnings("unchecked")
			HashMap<String, Long> counters = TCA_Timer.getInstance().getMeasurementForSize(i);
			long sum = 0;
			for (Long l : counters.values()) {
				sum += l;
			}
			measurements.add(i, sum);
		}
		
		RegressionPlotChart scatterPlot = new RegressionPlotChart(title, measurements);
		scatterPlot.show();
	}

}