package tca.instrumentation;

import java.util.HashMap;

public class TCA_Timer {

	private static Integer currentSize = 1;
	private static HashMap<String,Long> lastTimestamp = new HashMap<String,Long>();
	private static HashMap<Integer, HashMap<String, Long>> measurements = new HashMap<Integer, HashMap<String, Long>>();

	public static void setSize(int size){
		TCA_Timer.currentSize = size;
		
	}
	
	public static HashMap<String, Long> getCountersForSize(Integer size){
		if(!measurements.containsKey(size)){
			measurements.put(size, new HashMap<String, Long>());
		}
		return measurements.get(size);
	}
	
	public static synchronized void start(String key){
		lastTimestamp.remove(key); // just clear it out if there was already a record
		lastTimestamp.put(key, System.nanoTime());
	}
	
	public static synchronized void stop(String key){
		if(lastTimestamp.containsKey(key)){
			long delta = System.nanoTime()-lastTimestamp.remove(key);
			if(measurements.containsKey(currentSize)){
				measurements.get(currentSize).put(key, delta);
			} else {
				HashMap<String, Long> values = new HashMap<String, Long>();
				values.put(key, delta);
				measurements.put(currentSize, values);
			}
		}
	}
	
}
