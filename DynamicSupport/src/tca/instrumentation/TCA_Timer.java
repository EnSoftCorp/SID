package tca.instrumentation;

import java.util.HashMap;

public class TCA_Timer extends MeasurementProbe {

	private static TCA_Timer instance = null;
	
	private TCA_Timer(){}

	public static TCA_Timer getInstance() {
		if (instance == null) {
			instance = new TCA_Timer();
		}
		return instance;
	}
	
	private HashMap<String,Long> lastTimestamp = new HashMap<String,Long>();

	public void setSize(int size){
		currentSize = size;
	}
	
	public synchronized void start(String key){
		lastTimestamp.remove(key); // just clear it out if there was already a record
		lastTimestamp.put(key, System.nanoTime());
	}
	
	public synchronized void stop(String key){
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
	
	public static synchronized void probe(String key) {
		TCA_Timer instance = getInstance();
		if(instance.lastTimestamp.containsKey(key)){
			instance.stop(key);
		} else {
			instance.start(key);
		}
	}
}
