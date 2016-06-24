package tca.instrumentation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

public class TCA_Counter {

	public static Integer currentSize = 1;
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
	
	public static HashMap<Integer, Long> getKeyMeasurements(String key){
		HashMap<Integer,Long> result = new HashMap<Integer,Long>();
		for(Entry<Integer, HashMap<String, Long>> entry : measurements.entrySet()){
			if(entry.getValue().containsKey(key)){
				result.put(entry.getKey(), entry.getValue().get(key));
			}
		}
		return result;
	}
	
	public static HashSet<String> getKeys(){
		HashSet<String> keys = new HashSet<String>();
		for(Entry<Integer, HashMap<String, Long>> entry : measurements.entrySet()){
			for(String key : entry.getValue().keySet()){
				keys.add(key);
			}
		}
		return keys;
	}

	/**
	 * Writes a CSV file of all the key measurements in the format
	 * KEY,WORKLOAD_SIZE,COUNT
	 * @param outputFile
	 * @throws IOException  
	 */
	public static void saveKeyMeasurements(File outputFile) throws IOException {
		FileWriter fw = new FileWriter(outputFile);
		fw.write("KEY,WORKLOAD_SIZE,COUNT\n");
		for(String key : getKeys()){
			for(Entry<Integer, Long> keyMeasurement : getKeyMeasurements(key).entrySet()){
				fw.write(key + "," + keyMeasurement.getKey() + "," + keyMeasurement.getValue() + "\n");
			}
		}
		fw.close();
	}
}
