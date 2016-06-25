package drivers;

import java.io.File;
import java.io.IOException;

import tca.instrumentation.TCA_Counter;

public class TimedRunDriver {

	public static final long TIMER = 1000*10; // run for 10 seconds
	
	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		
		final long start = System.currentTimeMillis();
		
		Thread t = new Thread(new Runnable(){
			@Override
			public void run() {
				// TODO: enable a main method by uncommenting the correct program entry point
TCA_MAIN_METHODS
			}
		});
		
		t.start();
		
		// doing this really grossly because shutdown hooks don't work in signal interrupts
		while((System.currentTimeMillis() - start) < TIMER){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {}
		}
		t.stop(); // I feel dirty...:(
		
		File outputFile = new File("measurements.csv");
		System.out.println("Saving measurements to " + outputFile.getAbsolutePath());
		try {
			TCA_Counter.saveKeyMeasurements(outputFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}