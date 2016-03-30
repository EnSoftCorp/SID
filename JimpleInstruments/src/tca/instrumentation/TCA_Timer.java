package tca.instrumentation;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class TCA_Timer {

	private static HashMap<String,Long> lastTimestamp = new HashMap<String,Long>();
	
	public static synchronized void start(String logFile){
		lastTimestamp.remove(logFile); // just clear it out if there was already a record
		lastTimestamp.put(logFile, System.currentTimeMillis());
	}
	
	public static synchronized void stop(String logFile){
		if(lastTimestamp.containsKey(logFile)){
			try {
				FileWriter writer = new FileWriter(logFile, true);
				writer.write((System.currentTimeMillis()-lastTimestamp.remove(logFile)) + "\n");
				writer.close();
			} catch (IOException e) {
				// TODO: Log error
			}
		}
	}
	
}
