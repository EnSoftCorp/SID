package tca.instrumentation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

public abstract class MeasurementProbe implements Probe {

	protected Integer currentSize = 1;
	protected HashMap<Integer, HashMap<String, Long>> measurements = new HashMap<Integer, HashMap<String, Long>>();

	public abstract void setSize(int size);
	
	public HashMap<String, Long> getCountersForSize(Integer size){
		if(!measurements.containsKey(size)){
			measurements.put(size, new HashMap<String, Long>());
		}
		return measurements.get(size);
	}
	
	public HashMap<Integer, Long> getKeyMeasurements(String key){
		HashMap<Integer,Long> result = new HashMap<Integer,Long>();
		for(Entry<Integer, HashMap<String, Long>> entry : measurements.entrySet()){
			if(entry.getValue().containsKey(key)){
				result.put(entry.getKey(), entry.getValue().get(key));
			}
		}
		return result;
	}
	
	public HashSet<String> getKeys(){
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
	public void saveKeyMeasurements(File outputFile) throws IOException {
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
