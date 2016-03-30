package tca.instrumentation;

import java.util.HashMap;

public class TCA_Counter {

	private static Integer currentSize = 1;
	private static HashMap<Integer, HashMap<String, Long>> measurements = new HashMap<Integer, HashMap<String, Long>>();

	public static void setSize(int size){
		TCA_Counter.currentSize = size;
	}
	
	public static synchronized void incrementCounter(String key) {
		HashMap<String, Long> counters = getCountersForSize(currentSize);
		Long counterValue = 0L;
		if (counters.containsKey(key)) {
			counterValue = counters.remove(key);
		}
		counterValue++;
		counters.put(key, counterValue);
	}
	
	public static HashMap<String, Long> getCountersForSize(Integer size){
		if(!measurements.containsKey(size)){
			measurements.put(size, new HashMap<String, Long>());
		}
		return measurements.get(size);
	}

}
