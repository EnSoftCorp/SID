package tca.instrumentation;

import java.util.HashMap;

public class TCA_Counter extends MeasurementProbe {

	private static TCA_Counter instance = null;
	
	private TCA_Counter(){}

	public static TCA_Counter getInstance() {
		if (instance == null) {
			instance = new TCA_Counter();
		}
		return instance;
	}
	   
	public void setSize(int size){
		currentSize = size;
	}
	
	private synchronized void incrementCounter(String key) {
		HashMap<String, Long> counters = getCountersForSize(currentSize);
		Long counterValue = 0L;
		if (counters.containsKey(key)) {
			counterValue = counters.remove(key);
		}
		counterValue++;
		counters.put(key, counterValue);
	}
	
	public static synchronized void probe(String key) {
		getInstance().incrementCounter(key);
	}
	
}
