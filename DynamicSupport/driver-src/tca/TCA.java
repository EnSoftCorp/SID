package tca;

import java.io.IOException;
import java.util.HashMap;

import tca.instrumentation.TCA_Counter;

public class TCA {

	public static void plotRegression(final String title, final int TOTAL_WORK_TASKS) throws IOException, InterruptedException {
		Measurements measurements = new Measurements();
		for (int i = 1; i <= TOTAL_WORK_TASKS; i++) {
			@SuppressWarnings("unchecked")
			HashMap<String, Long> counters = TCA_Counter.getCountersForSize(i);
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